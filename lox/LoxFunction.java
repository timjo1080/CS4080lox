package lox;

import java.util.List;

class LoxFunction implements LoxCallable {
  private final String name;
  private final Expr.Function declaration;
  private final Environment closure;
  private final boolean isInitializer;

  LoxFunction(String name, Expr.Function declaration, Environment closure,
              boolean isInitializer) {
    this.isInitializer = isInitializer;
    this.name = name;
    this.closure = closure;
    this.declaration = declaration;
  }

  LoxFunction bind(LoxInstance instance) {
    Environment environment = new Environment(closure);
    environment.define(instance);
    return new LoxFunction(name, declaration, environment,
                           isInitializer);
  }

  @Override
  public Object call(Interpreter interpreter,
                     List<Object> arguments) {
    Environment environment = new Environment(closure);
    if(declaration.params != null) {
      for (int i = 0; i < declaration.params.size(); i++) {
        environment.define(arguments.get(i));
      }
    }

    try {
      interpreter.executeBlock(declaration.body, environment);
    } catch (Return returnValue) {
      if (isInitializer) return closure.getAt(0, 0);
      
      return returnValue.value;
    }

    if (isInitializer) return closure.getAt(0, 0);
    return null;
  }

  @Override
  public int arity() {
    return declaration.params.size();
  }

  @Override
  public String toString() {
      if (name == null) return "<fn>";
      return "<fn " + name + ">";
  }

  public boolean isGetter()
    {
        return declaration.params == null;
    }
}