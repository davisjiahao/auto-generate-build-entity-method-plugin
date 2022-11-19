package com.github.davisjiahao.plugin.entity;

import com.intellij.psi.PsiType;

/**
 * CreateMethodParam
 *
 * @author daviswujiahao
 * @date 2022/11/19 17:54
 * @since 1.0
 **/
public class CreateMethodParam {
    private String name;
    private PsiType psiType;

    public String getName() {
        return name;
    }

    public PsiType getPsiType() {
        return psiType;
    }

    public CreateMethodParam(String name, PsiType psiType) {
        this.name = name;
        this.psiType = psiType;
    }
}
