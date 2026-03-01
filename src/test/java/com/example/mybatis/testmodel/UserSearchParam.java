package com.example.mybatis.testmodel;

import java.util.List;

public class UserSearchParam {
    private String name;
    private Integer age;
    private List<Integer> ids;
    private boolean active;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public List<Integer> getIds() { return ids; }
    public void setIds(List<Integer> ids) { this.ids = ids; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
