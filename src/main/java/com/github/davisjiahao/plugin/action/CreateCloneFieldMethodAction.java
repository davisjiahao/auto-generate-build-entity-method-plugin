// Copyright 2000-2022 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.github.davisjiahao.plugin.action;

import com.github.davisjiahao.plugin.utils.JavaPoetClassNameUtils;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import kotlin.jvm.internal.Intrinsics;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Implements an intention action to replace a ternary statement with if-then-else.
 */
@NonNls
public class CreateCloneFieldMethodAction extends PsiElementBaseIntentionAction implements IntentionAction {

  protected final String setRegex = "set(\\w+)";
  protected final String getRegex = "get(\\w+)";

  /**
   * If this action is applicable, returns the text to be shown in the list of intention actions available.
   */
  @Override
  @NotNull
  public String getText() {
    return "Create clone field method";
  }

  /**
   * Returns text for name of this family of intentions.
   * It is used to externalize "auto-show" state of intentions.
   * It is also the directory name for the descriptions.
   *
   * @return the intention family name.
   */
  @Override
  @NotNull
  public String getFamilyName() {
    return "Create method from usage";
  }

  /**
   * Checks whether this intention is available at the caret offset in file - the caret must sit just before a "?"
   * character in a ternary statement. If this condition is met, this intention's entry is shown in the available
   * intentions list.
   *
   * <p>Note: this method must do its checks quickly and return.</p>
   *
   * @param project a reference to the Project object being edited.
   * @param editor  a reference to the object editing the project source
   * @param element a reference to the PSI element currently under the caret
   * @return {@code true} if the caret is in a literal string element, so this functionality should be added to the
   * intention menu or {@code false} for all other types of caret positions
   */
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
    // Quick sanity check
    if (element == null) {
      return false;
    }
    if (!(element.getParent() != null && element.getParent().getParent() != null && element.getParent().getParent() instanceof PsiMethodCallExpression)) {
      return false;
    }

    return true;
  }


  @NotNull
  public List<Param> getExpectedParameters(PsiMethodCallExpression call) {
    PsiExpressionList callArgumentList = call.getArgumentList();
    GlobalSearchScope scope = call.getResolveScope();
    Project project = call.getProject();
    JavaCodeStyleManager codeStyleManager = project.getService(JavaCodeStyleManager.class, true);
    PsiExpression[] psiExpressions = callArgumentList.getExpressions();

    List<Param> result = Lists.newArrayList();
    for(int i = 0; i < psiExpressions.length; ++i) {
      PsiExpression psiExpression = psiExpressions[i];
      PsiType type = this.getArgType(psiExpression, scope);
      String name = codeStyleManager.suggestSemanticNames(psiExpression).stream().findFirst().get();
      result.add(new Param(name, type));
    }

    return result;
  }

  private static class Param {
    private String name;
    private PsiType psiType;

    public String getName() {
      return name;
    }

    public PsiType getPsiType() {
      return psiType;
    }

    public Param(String name, PsiType psiType) {
      this.name = name;
      this.psiType = psiType;
    }
  }

  private final PsiType getArgType(PsiExpression expression, GlobalSearchScope scope) {
    PsiType argType = RefactoringUtil.getTypeByExpression(expression);
    if (argType != null && !Intrinsics.areEqual(PsiType.NULL, argType) && !LambdaUtil.notInferredType(argType)) {
      if (argType instanceof PsiDisjunctionType) {
        return ((PsiDisjunctionType)argType).getLeastUpperBound();
      } else if (argType instanceof PsiWildcardType) {
        return ((PsiWildcardType)argType).isBounded() ? ((PsiWildcardType)argType).getBound() : (PsiType)PsiType.getJavaLangObject(expression.getManager(), scope);
      } else {
        return argType;
      }
    } else {
      return PsiType.getJavaLangObject(expression.getManager(), scope);
    }
  }

  /**
   * Modifies the Psi to change a ternary expression to an if-then-else statement.
   * If the ternary is part of a declaration, the declaration is separated and moved above the if-then-else statement.
   * Called when user selects this intention action from the available intentions list.
   *
   * @param project a reference to the Project object being edited.
   * @param editor  a reference to the object editing the project source
   * @param element a reference to the PSI element currently under the caret
   * @throws IncorrectOperationException Thrown by underlying (Psi model) write action context
   *                                     when manipulation of the psi tree fails.
   * @see
   */
  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element)
          throws IncorrectOperationException {

    PsiMethodCallExpression parent = (PsiMethodCallExpression) (element.getParent().getParent());
    List<Param> params = getExpectedParameters(parent);

    PsiClass targetClass = doCollectRequests(parent.getMethodExpression());

    @NotNull ExpectedTypeInfo[] expectedTypeInfos = CreateFromUsageUtils.guessExpectedTypes(parent.getMethodExpression(),
            parent.getMethodExpression().getParent() instanceof PsiStatement);
    List<ExpectedTypeInfo> psiTypes = Arrays.stream(expectedTypeInfos).collect(Collectors.toList());

    parent.getMethodExpression().getReferenceName();
    MethodSpec psiMethod = transformMethod(project, psiTypes.get(0).getType(), params);

    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiElement insert = factory.createMethodFromText(psiMethod.toString(), targetClass);

    PsiElement anchor = getAnchor(targetClass);
    if (anchor != null) {
      targetClass.addAfter(insert, anchor);
    } else {
      targetClass.add(insert);
    }


    CodeStyleManager.getInstance(project).reformat(insert);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(targetClass);
  }

  private final PsiClass doCollectRequests(PsiReferenceExpression myRef) {
    PsiExpression qualifier = myRef.getQualifierExpression();
    if (qualifier != null) {
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(qualifier.getType());

      if (psiClass != null) {
        return psiClass;
      }

      if (!(qualifier instanceof PsiJavaCodeReferenceElement)) {
        return null;
      }

      PsiElement psiElement = ((PsiJavaCodeReferenceElement) qualifier).resolve();
      if (!(psiElement instanceof PsiClass)) {
        return null;
      }
      return  (PsiClass) psiElement;
    }
    return null;
  }


  public MethodSpec transformMethod(Project project, PsiType returnType, List<Param> params) {

    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();

    List<String> names = Lists.newArrayList();
    List<PsiType> psiTypes = Lists.newArrayList();
    params.forEach(param-> {
      names.add(param.getName());
      psiTypes.add(param.getPsiType());
    });
    PsiParameterList parameterList = factory.createParameterList(names.toArray(new String[0]), psiTypes.toArray(new PsiType[0]));
    TypeName returnTypeName = Optional.ofNullable(returnType)
            .map(JavaPoetClassNameUtils::guessType)
            .orElse(TypeName.VOID);

    return MethodSpec.methodBuilder("build")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addParameters(transformParameter(parameterList.getParameters()))
            .addCode(buildCodeBody(returnType, params))
            .returns(returnTypeName)
            .build();
  }

  private String buildCodeBody(PsiType returnType, List<Param> params) {

    Pattern setMtd = Pattern.compile(setRegex);
    // 获取类的set方法并存放起来
    List<String> targetKey = new ArrayList<>();
    Map<String, String> paramMtdMapTarget = new HashMap<>();
    PsiClass psiClass = PsiTypesUtil.getPsiClass(returnType);
    List<PsiClass> psiClassLinkList = getPsiClassLinkList(psiClass);
    for (PsiClass psi : psiClassLinkList) {
      List<String> methodsList = getMethods(psi, setRegex, "set");
      for (String methodName : methodsList) {
        // 替换属性
        String param = setMtd.matcher(methodName).replaceAll("$1").toLowerCase();
        // 保存获取的属性信息
        paramMtdMapTarget.put(param, methodName);
        targetKey.add(param);
      }
    }

    PsiType psiTypeParam = params.get(0).getPsiType();
    PsiClass psiClassParam = PsiTypesUtil.getPsiClass(psiTypeParam);
    List<PsiClass> psiClassLinkListParam = getPsiClassLinkList(psiClassParam);
    Map<String, String> paramMtdMapParam = new HashMap<>();
    Pattern getM = Pattern.compile(getRegex);
    for (PsiClass psi : psiClassLinkListParam) {
      List<String> methodsList = getMethods(psi, getRegex, "get");
      for (String methodName : methodsList) {
        String param = getM.matcher(methodName).replaceAll("$1").toLowerCase();
        paramMtdMapParam.put(param, methodName);
      }
    }

    StringBuilder code = new StringBuilder(psiClass.getQualifiedName() + " newEntity = new " + psiClass.getQualifiedName() + "();\n");
    for (String param : targetKey) {
      code.append("newEntity.").
              append(paramMtdMapTarget.get(param)).append("(").
              append(null == paramMtdMapParam.get(param) ? "" : params.get(0).getName() + "." + paramMtdMapParam.get(param) + "()").
              append(");\n");
    }

    return code.toString();
  }


  protected List<PsiClass> getPsiClassLinkList(PsiClass psiClass) {
    List<PsiClass> psiClassList = new ArrayList<>();
    PsiClass currentClass = psiClass;
    while (null != currentClass && !"Object".equals(currentClass.getName())) {
      psiClassList.add(currentClass);
      currentClass = currentClass.getSuperClass();
    }
    Collections.reverse(psiClassList);
    return psiClassList;
  }

  private boolean isUsedLombok(PsiClass psiClass) {
    return null != psiClass.getAnnotation("lombok.Data");
  }

  protected List<String> getMethods(PsiClass psiClass, String regex, String typeStr) {
    PsiMethod[] methods = psiClass.getMethods();
    List<String> methodList = new ArrayList<>();

    // 判断使用了 lombok，需要补全生成 get、set
    if (isUsedLombok(psiClass)) {
      Pattern p = Pattern.compile("static.*?final|final.*?static");
      PsiField[] fields = psiClass.getFields();
      for (PsiField psiField : fields) {
        String fieldVal = Objects.requireNonNull(psiField.getNameIdentifier().getContext()).getText();
        // serialVersionUID 判断
        if (fieldVal.contains("serialVersionUID")){
          continue;
        }
        // static final 常量判断过滤
        Matcher matcher = p.matcher(fieldVal);
        if (matcher.find()){
          continue;
        }
        String name = psiField.getNameIdentifier().getText();
        methodList.add(typeStr + name.substring(0, 1).toUpperCase() + name.substring(1));
      }

      for (PsiMethod method : methods) {
        String methodName = method.getName();
        if (Pattern.matches(regex, methodName) && !methodList.contains(methodName)) {
          methodList.add(methodName);
        }
      }

      return methodList;
    }


    // 正常创建的get、set，直接获取即可
    for (PsiMethod method : methods) {
      String methodName = method.getName();
      if (Pattern.matches(regex, methodName)) {
        methodList.add(methodName);
      }
    }

    return methodList;
  }

  private List<ParameterSpec> transformParameter(PsiParameter[] parameters) {
    if (ArrayUtils.isEmpty(parameters)) {
      return Collections.emptyList();
    }

    return Arrays.stream(parameters)
            .map(v -> ParameterSpec.builder(JavaPoetClassNameUtils.guessType(v.getType()), v.getName()).build())
            .collect(Collectors.toList());
  }

  @Nullable
  public final PsiElement getAnchor(@NotNull PsiClass psiClass) {
    PsiMethod lastMethod = null;
    PsiMethod[] allMethods = psiClass.getMethods();
    if(allMethods != null && allMethods.length > 0){
      lastMethod = allMethods[allMethods.length-1];
    }
    //add the method as the last method of the class if possible
    return lastMethod;
  }

  /**
   * Indicates this intention action expects the Psi framework to provide the write action context for any changes.
   *
   * @return {@code true} if the intention requires a write action context to be provided or {@code false} if this
   * intention action will start a write action
   */
  @Override
  public boolean startInWriteAction() {
    return true;
  }

}
