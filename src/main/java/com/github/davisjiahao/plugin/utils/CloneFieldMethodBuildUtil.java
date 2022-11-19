package com.github.davisjiahao.plugin.utils;

import com.github.davisjiahao.plugin.entity.CreateMethodParam;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.text.DateFormatUtil;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import kotlin.jvm.internal.Intrinsics;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;

import javax.lang.model.element.Modifier;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CloneFieldMethodBuildUtil
 *
 * @author daviswujiahao
 * @date 2022/11/19 17:52
 * @since 1.0
 **/
public class CloneFieldMethodBuildUtil {

    protected static final String SET_REGEX = "set(\\w+)";

    protected static final String GET_REGEX = "get(\\w+)";

    public static String buildCodeBody(PsiType returnType, List<CreateMethodParam> params, boolean isMatched) {

        Pattern setMtd = Pattern.compile(SET_REGEX);

        // 获取类的set方法并存放起来
        List<String> targetKey = new ArrayList<>();
        Map<String, String> paramMtdMapTarget = new HashMap<>();
        PsiClass psiClass = PsiTypesUtil.getPsiClass(returnType);
        List<PsiClass> psiClassLinkList = getPsiClassLinkList(psiClass);
        for (PsiClass psi : psiClassLinkList) {
            List<String> methodsList = getMethods(psi, SET_REGEX, "set");
            for (String methodName : methodsList) {
                // 替换属性
                String param = setMtd.matcher(methodName).replaceAll("$1").toLowerCase();
                // 保存获取的属性信息
                paramMtdMapTarget.put(param, methodName);
                targetKey.add(param);
            }
        }

        Map<String, SetParam> getMethodMap = buildGetMethodMap(params);

        StringBuilder code = new StringBuilder(psiClass.getQualifiedName() + " newEntity = new " + psiClass.getQualifiedName() + "();\n");
        for (String param : targetKey) {
            if (isMatched && null == getMethodMap.get(param)) {
                continue;
            }
            code.append("newEntity.").
                    append(paramMtdMapTarget.get(param)).append("(").
                    append(null == getMethodMap.get(param) ? "" : getMethodMap.get(param).getParamName() + "." + getMethodMap.get(param).getMethodName()).
                    append(null == getMethodMap.get(param) || getMethodMap.get(param).isInNotComplex() ? "" : "()").
                    append(");\n");
        }
        code.append("return newEntity;");
        return code.toString();
    }

    protected static Map<String, SetParam> buildGetMethodMap(List<CreateMethodParam> params) {

        Map<String, SetParam> result = Maps.newHashMap();

        for (CreateMethodParam createMethodParam : params) {
            PsiType psiTypeParam = createMethodParam.getPsiType();
            PsiClass psiClassParam = PsiTypesUtil.getPsiClass(psiTypeParam);
            if (psiClassParam == null || getJavaBaseTypeDefaultValue(psiClassParam.getName()) != null) {
                if (result.containsKey(createMethodParam.getName())) {
                    continue;
                }
                result.put(createMethodParam.getName().toLowerCase(), new SetParam(true, createMethodParam.getName(), createMethodParam.getName()));
            } else {
                List<PsiClass> psiClassLinkListParam = getPsiClassLinkList(psiClassParam);
                Pattern getM = Pattern.compile(GET_REGEX);
                for (PsiClass psi : psiClassLinkListParam) {
                    List<String> methodsList = getMethods(psi, GET_REGEX, "get");
                    for (String methodName : methodsList) {
                        String param = getM.matcher(methodName).replaceAll("$1").toLowerCase();
                        if (result.containsKey(param)) {
                            continue;
                        }
                        result.put(param, new SetParam(false, methodName, createMethodParam.getName()));
                    }
                }
            }
        }
        return result;
    }

    public static class SetParam {
        private boolean isInNotComplex;
        private String methodName;
        private String paramName;

        public SetParam(boolean isInNotComplex, String methodName, String paramName) {
            this.isInNotComplex = isInNotComplex;
            this.methodName = methodName;
            this.paramName = paramName;
        }

        public boolean isInNotComplex() {
            return isInNotComplex;
        }

        public void setInNotComplex(boolean inNotComplex) {
            isInNotComplex = inNotComplex;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        public String getParamName() {
            return paramName;
        }

        public void setParamName(String paramName) {
            this.paramName = paramName;
        }
    }

    /**
     * 判断是否是基本类型
     *
     * @param paramType
     * @return
     */
    public static Object getJavaBaseTypeDefaultValue(String paramType) {
        Object paramValue = null;
        switch (paramType.toLowerCase()) {
            case "byte":
                paramValue = Byte.valueOf("1");
                break;
            case "char":
                paramValue = Character.valueOf('Z');
                break;
            case "character":
                paramValue = Character.valueOf('Z');
                break;
            case "boolean":
                paramValue = Boolean.TRUE;
                break;
            case "int":
                paramValue = Integer.valueOf(1);
                break;
            case "integer":
                paramValue = Integer.valueOf(1);
                break;
            case "double":
                paramValue = Double.valueOf(1);
                break;
            case "float":
                paramValue = Float.valueOf(1.0F);
                break;
            case "long":
                paramValue = Long.valueOf(1L);
                break;
            case "short":
                paramValue = Short.valueOf("1");
                break;
            case "bigdecimal":
                return BigDecimal.ONE;
            case "string":
                paramValue = "demoData";
                break;
            case "date":
                paramValue = DateFormatUtil.formatDateTime(new Date());
                break;
            case "datetime":
                paramValue = DateFormatUtil.formatDateTime(new Date());
                break;
        }
        return paramValue;
    }

    protected static List<PsiClass> getPsiClassLinkList(PsiClass psiClass) {
        List<PsiClass> psiClassList = new ArrayList<>();
        PsiClass currentClass = psiClass;
        while (null != currentClass && !"Object".equals(currentClass.getName())) {
            psiClassList.add(currentClass);
            currentClass = currentClass.getSuperClass();
        }
        Collections.reverse(psiClassList);
        return psiClassList;
    }

    private static boolean isUsedLombok(PsiClass psiClass) {
        return null != psiClass.getAnnotation("lombok.Data");
    }

    protected static List<String> getMethods(PsiClass psiClass, String regex, String typeStr) {
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


    @NotNull
    public static List<CreateMethodParam> getExpectedParameters(PsiMethodCallExpression call) {
        PsiExpressionList callArgumentList = call.getArgumentList();
        GlobalSearchScope scope = call.getResolveScope();
        Project project = call.getProject();
        JavaCodeStyleManager codeStyleManager = project.getService(JavaCodeStyleManager.class, true);
        PsiExpression[] psiExpressions = callArgumentList.getExpressions();

        List<CreateMethodParam> result = Lists.newArrayList();
        for(int i = 0; i < psiExpressions.length; ++i) {
            PsiExpression psiExpression = psiExpressions[i];
            PsiType type = getArgType(psiExpression, scope);
            String name = codeStyleManager.suggestSemanticNames(psiExpression).stream().findFirst().get();
            result.add(new CreateMethodParam(name, type));
        }

        return result;
    }



    public static final PsiType getArgType(PsiExpression expression, GlobalSearchScope scope) {
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


    public static MethodSpec transformMethod(Project project, PsiType returnType, List<CreateMethodParam> params, String methodName, boolean isMatched) {

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

        return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameters(transformParameter(parameterList.getParameters()))
                .addCode(CloneFieldMethodBuildUtil.buildCodeBody(returnType, params, isMatched))
                .returns(returnTypeName)
                .build();
    }


    private static List<ParameterSpec> transformParameter(PsiParameter[] parameters) {
        if (ArrayUtils.isEmpty(parameters)) {
            return Collections.emptyList();
        }

        return Arrays.stream(parameters)
                .map(v -> ParameterSpec.builder(JavaPoetClassNameUtils.guessType(v.getType()), v.getName()).build())
                .collect(Collectors.toList());
    }

}
