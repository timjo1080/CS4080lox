package lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Environment {
    final Environment enclosing;
    // private final Map<String, Object> values = new HashMap<>();
    private final List<Object> values = new ArrayList<>(); 

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    // locals are found through distance and slot, so we don't need to look up by name anymore

    // Object get(Token name) {
    //     if (values.containsKey(name.lexeme)) {
    //     return values.get(name.lexeme);
    //     }

    //     if (enclosing != null) return enclosing.get(name);

    //     throw new RuntimeError(name,
    //         "Undefined variable '" + name.lexeme + "'.");
    // }
    // void assign(Token name, Object value) {
    //     if (values.containsKey(name.lexeme)) {
    //     values.put(name.lexeme, value);
    //     return;
    //     }

    //     if (enclosing != null) {
    //     enclosing.assign(name, value);
    //     return;
    //     }

    //     throw new RuntimeError(name,
    //         "Undefined variable '" + name.lexeme + "'.");
    // } 

    void define( Object value) {
        values.add(value);
    }

    Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
        environment = environment.enclosing; 
        }

        return environment;
    }

    Object getAt(int distance, int slot) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }
        return ancestor(distance).values.get(slot);
    }

    void assignAt(int distance, int slot, Object value) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }
        ancestor(distance).values.set(slot, value);
    }
}