package com.example.mybatis.extractor;

/**
 * 動的SQLの分岐パターンを表す列挙型。
 */
public enum BranchPattern {
    /** 全分岐ON（全<if>条件がtrue） - 最大SQL */
    ALL_SET,
    /** 全分岐OFF（全<if>条件がfalse） - 最小SQL */
    ALL_NULL
}
