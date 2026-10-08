package lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import lox.Expr.Conditional;
import lox.Expr.Function;
import lox.Stmt.Break;
import lox.Stmt.Expression;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;
  private final Stack<Map<String, Variable>> scopes = new Stack<>();
  private FunctionType currentFunction = FunctionType.NONE;

  Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  private enum FunctionType {
    NONE,
    FUNCTION,
    INITIALIZER,
    METHOD
  }

    private enum ClassType {
    NONE,
    CLASS
  }

  private ClassType currentClass = ClassType.NONE;


  private enum VariableState {
    DECLARED,
    DEFINED,
    READ
  }

  private static class Variable {
    VariableState state;
    final Token name;
    final int slot;

    Variable(Token name, VariableState state, int slot) {
      this.name = name;
      this.state = state;
      this.slot = slot;
    }
  }

  void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    beginScope();
    resolve(stmt.statements);
    endScope();
    return null;
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    ClassType enclosingClass = currentClass;
    currentClass = ClassType.CLASS;
    
    declare(stmt.name);
    define(stmt.name);

    beginScope();
    scopes.peek().put("this", new Variable(stmt.name, VariableState.READ, 0));

    for (Stmt.Function method : stmt.methods) {
      FunctionType declaration = FunctionType.METHOD;
      if (method.name.lexeme.equals("init")) {
        declaration = FunctionType.INITIALIZER;
      }
      resolveFunction(method.function, declaration); 
    }

    endScope();
    currentClass = enclosingClass;
    return null;
  }

  

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    declare(stmt.name);
    define(stmt.name);

    resolveFunction(stmt.function, FunctionType.FUNCTION);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);
    if (stmt.elseBranch != null) resolve(stmt.elseBranch);
    return null;
  }

  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    if (currentFunction == FunctionType.NONE) {
      Lox.error(stmt.keyword, "Can't return from top-level code.");
    }

    if (stmt.value != null) {
      if (currentFunction == FunctionType.INITIALIZER) {
        Lox.error(stmt.keyword,
            "Can't return a value from an initializer.");
      }
      resolve(stmt.value);
    }

    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    declare(stmt.name);
    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }
    define(stmt.name);
    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    resolve(stmt.condition);
    resolve(stmt.body);
    return null;
  }

  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    resolve(expr.value);
    resolveLocal(expr, expr.name, false);
    return null;
  }

  @Override
  public Void visitBinaryExpr(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitCallExpr(Expr.Call expr) {
    resolve(expr.callee);

    for (Expr argument : expr.arguments) {
      resolve(argument);
    }

    return null;
  }

  @Override
  public Void visitGetExpr(Expr.Get expr) {
    resolve(expr.object);
    return null;
  }

  @Override
  public Void visitGroupingExpr(Expr.Grouping expr) {
    resolve(expr.expression);
    return null;
  }

  @Override
  public Void visitLiteralExpr(Expr.Literal expr) {
    return null;
  }

  @Override
  public Void visitLogicalExpr(Expr.Logical expr) {
    resolve(expr.left);
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitSetExpr(Expr.Set expr) {
    resolve(expr.value);
    resolve(expr.object);
    return null;
  }

  @Override
  public Void visitThisExpr(Expr.This expr) {
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword,
          "Can't use 'this' outside of a class.");
      return null;
    }
    
    resolveLocal(expr, expr.keyword, true);
    return null;
  }

  @Override
  public Void visitUnaryExpr(Expr.Unary expr) {
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitVariableExpr(Expr.Variable expr) {
      if (!scopes.isEmpty()) {
          var variableInfo = scopes.peek().get(expr.name.lexeme);
          if (variableInfo != null && variableInfo.state == VariableState.DECLARED) {
              Lox.error(expr.name, "Can't read local variable in its own initializer.");
          }
      }

      resolveLocal(expr, expr.name, true);
      return null;
  }

  private void resolve(Stmt stmt) {
    stmt.accept(this);
  }

  private void resolve(Expr expr) {
    expr.accept(this);
  }

  private void resolveFunction(
    Expr.Function function, FunctionType type) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;

    beginScope();
    for (Token param : function.params) {
      declare(param);
      define(param);
    }
    resolve(function.body);
    endScope();
    currentFunction = enclosingFunction;
  }

  private void beginScope() {
    scopes.push(new HashMap<String, Variable>());   
  }

  private void endScope() {
    Map<String, Variable> scope = scopes.pop();
    for(Map.Entry<String, Variable> entry : scope.entrySet()) {
        if(entry.getValue().state == VariableState.DEFINED) {
            Lox.error(entry.getValue().name, "Variable is defined but never read.");
        }
    }
  }

  private void declare(Token name) {
    if (scopes.isEmpty()) return;

    Map<String, Variable> scope = scopes.peek();
    if (scope.containsKey(name.lexeme)) {
      Lox.error(name,
          "Already a variable with this name in this scope.");
    }

    scope.put(name.lexeme,
      new Variable(name, VariableState.DECLARED, scope.size()));
  }

  private void define(Token name) {
    if (scopes.isEmpty()) return;
    scopes.peek().get(name.lexeme).state = VariableState.DEFINED;
  }

  private void resolveLocal(Expr expr, Token name, boolean isRead) {
  for (int i = scopes.size() - 1; i >= 0; i--) {
    Variable v = scopes.get(i).get(name.lexeme);
    if (v != null) {
      interpreter.resolve(expr, scopes.size() - 1 - i, v.slot);

      if (isRead) {
        v.state = VariableState.READ;
      }
      return;
    }
  }
}

  @Override
    public Void visitBreakStmt(Stmt.Break stmt) {
        return null;
    }

  @Override
   public Void visitConditionalExpr(Expr.Conditional expr) {
     resolve(expr.condition);
     resolve(expr.thenBranch);
     resolve(expr.elseBranch);
     return null;
   }

  @Override
    public Void visitFunctionExpr(Expr.Function expr) {
        resolveFunction(expr, FunctionType.FUNCTION);
        return null;
    }
}