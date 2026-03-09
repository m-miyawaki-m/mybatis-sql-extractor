package com.example.mybatis.parameter;

import java.util.HashMap;

/**
 * 全キーに対してnullを返すMap実装。
 * MyBatisのOGNL評価で &lt;if test="x != null"&gt; を全てfalseにする。
 * containsKey()はtrueを返し、プロパティ存在チェックを通過させる。
 */
public class NullParameterMap extends HashMap<String, Object> {

    @Override
    public Object get(Object key) {
        if (super.containsKey(key)) {
            return super.get(key);
        }
        return null;
    }

    @Override
    public boolean containsKey(Object key) {
        return true;
    }
}
