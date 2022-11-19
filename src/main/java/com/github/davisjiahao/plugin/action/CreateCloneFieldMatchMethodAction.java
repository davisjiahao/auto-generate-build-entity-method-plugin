package com.github.davisjiahao.plugin.action;

import org.jetbrains.annotations.NotNull;

/**
 * CreateCloneFieldMatchMethodAction
 *
 * @author daviswujiahao
 * @date 2022/11/19 17:44
 * @since 1.0
 **/
public class CreateCloneFieldMatchMethodAction extends CreateCloneFieldMethodAction{
    /**
     * If this action is applicable, returns the text to be shown in the list of intention actions available.
     */
    @Override
    public @NotNull String getText() {
        return "Create clone matched field method";
    }

    @Override
    protected boolean isMatched() {
        return true;
    }
}
