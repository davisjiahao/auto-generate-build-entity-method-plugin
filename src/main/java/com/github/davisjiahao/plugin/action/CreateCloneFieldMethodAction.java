// Copyright 2000-2022 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.github.davisjiahao.plugin.action;

import com.github.davisjiahao.plugin.entity.CreateMethodParam;
import com.github.davisjiahao.plugin.utils.CloneFieldMethodBuildUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.squareup.javapoet.MethodSpec;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implements an intention action to replace a ternary statement with if-then-else.
 */
@NonNls
public class CreateCloneFieldMethodAction extends PsiElementBaseIntentionAction implements IntentionAction {


  /**
   * If this action is applicable, returns the text to be shown in the list of intention actions available.
   */
  @Override
  @NotNull
  public String getText() {
    return "Create clone full field method";
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
    List<CreateMethodParam> params = CloneFieldMethodBuildUtil.getExpectedParameters(parent);

    PsiClass targetClass = doCollectRequests(parent.getMethodExpression());

    ExpectedTypeInfo[] expectedTypeInfos = CreateFromUsageUtils.guessExpectedTypes(parent.getMethodExpression(),
            parent.getMethodExpression().getParent() instanceof PsiStatement);
    List<ExpectedTypeInfo> psiTypes = Arrays.stream(expectedTypeInfos).collect(Collectors.toList());

    MethodSpec psiMethod = CloneFieldMethodBuildUtil.transformMethod(project, psiTypes.get(0).getType(), params, parent.getMethodExpression().getReferenceName(), isMatched());

    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiElement insert = factory.createMethodFromText(psiMethod.toString(), targetClass);

    targetClass.add(insert);

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

  protected boolean isMatched() {
    return false;
  }

}
