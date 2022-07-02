package jaerapps.functions.generateNames.util;

import java.util.Objects;

public class Person {
    private final String name;
    private final String email;
    private final String parentOverride;
    private final String group;

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getParentOverride() {
        return parentOverride;
    }

    public String getGroup() {
        return group;
    }

    private Person(String name, String email, String parentOverride, String group) {
        this.name = name;
        this.email = email;
        this.parentOverride = parentOverride;
        this.group = group;
    }

    public static Builder create() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return Objects.equals(name, person.name) && Objects.equals(email, person.email) && Objects.equals(parentOverride, person.parentOverride) && Objects.equals(group, person.group);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, email, parentOverride, group);
    }

    public static class Builder {
        private String name;
        private String email;
        private String parentOverride;
        private String group;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setEmail(String email) {
            this.email = email;
            return this;
        }

        public Builder setParentOverride(String parentOverride) {
            this.parentOverride = parentOverride;
            return this;
        }

        public Builder setGroup(String group) {
            this.group = group;
            return this;
        }

        public Person build() {
            return new Person(name, email, parentOverride, group);
        }
    }
}
