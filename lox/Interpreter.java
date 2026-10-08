package lox;

import java.util.List;
import java.util.Map;
import java.util.HashMap;


import lox.Expr.Conditional;

import java.util.ArrayList;


import lox.Stmt.Break;

class Interpreter implements Expr.Visitor<Object>,
                             Stmt.Visitor<Void>{
    final Map<String, Object> globals = new HashMap<>();
    private Environment environment = null;
    private static Object uninitialized = new Object();
    private final Map<Expr, Integer> locals = new HashMap<>();
    private final Map<Expr, Integer> slots = new HashMap<>();

    private static class BreakException extends RuntimeException {}
    
    Interpreter() {
        globals.put("clock", new LoxCallable() {
        @Override
        public int arity() { return 0; }

        @Override
        public Object call(Interpreter interpreter,
                            List<Object> arguments) {
            return (double)System.currentTimeMillis() / 1000.0;
        }

        @Override
        public String toString() { return "<native fn>"; }
        });
    }

    // chapter 8 challenge 1
    public void interpret(Expr expression) {
        try {
            Object value = evaluate(expression);
            System.out.println(stringify(value));
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR) {
        if (isTruthy(left)) return left;
        } else {
        if (!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        Object object = evaluate(expr.object);

        if (!(object instanceof LoxInstance)) { 
        throw new RuntimeError(expr.name,
                                "Only instances have fields.");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance)object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }   

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double)right;
        }

        // Unreachable.
        return null;
    }

    // chapter 8 challenge 2
    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        Object value;
        if (distance != null) {
            value = environment.getAt(distance, slots.get(expr));
        } else if (globals.containsKey(name.lexeme)) {
            value = globals.get(name.lexeme);
        } else {
            throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
        }
        if (value == uninitialized) {
            throw new RuntimeError(name, "Variable is not initialized");
        }
        return value;
    }
    @Override
    public Object visitConditionalExpr(Conditional expr) {
        Object condition = evaluate(expr.condition);
        if (isTruthy(condition)) {
            return evaluate(expr.thenBranch);
        } else {
            return evaluate(expr.elseBranch);
        }
    }


    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator,
                                      Object left, Object right) {
        if (left instanceof Double && right instanceof Double) return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "nil";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    void resolve(Expr expr, int depth, int slot) {
        locals.put(expr, depth); 
        slots.put(expr, slot);
    }

    void executeBlock(List<Stmt> statements,
                    Environment environment) {
        Environment previous = this.environment;
        try {
        this.environment = environment;

        for (Stmt statement : statements) {
            execute(statement);
        }
        } finally {
        this.environment = previous;
        }
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        
        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods) {
        LoxFunction function = new LoxFunction(method.name.lexeme, method.function,
         environment, method.name.lexeme.equals("init"));
        methods.put(method.name.lexeme, function);
        }

        LoxClass klass = new LoxClass(stmt.name.lexeme, methods);

        //adjusted due to no "get()" method in Environment class
        if (environment == null) {
            globals.put(stmt.name.lexeme, klass);
        } else {
            environment.define(klass);
        }
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }
    
    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
        execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
        execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) value = evaluate(stmt.value);

        throw new Return(value);
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = uninitialized;
        if (stmt.initializer != null) {
        value = evaluate(stmt.initializer);
        }

        define(stmt.name, value);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        try {
            while (isTruthy(evaluate(stmt.condition))) {
                execute(stmt.body);
            }
        } catch (BreakException e) {
            // Exit the loop when a break statement is encountered
        }
        return null;
    }
    public Void visitBreakStmt(Stmt.Break stmt) {
        throw new BreakException();
    }
    

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        
        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, slots.get(expr), value);
        } else {
            globals.put(expr.name.lexeme, value);
        }

        return value;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double)left > (double)right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left >= (double)right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left < (double)right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double)left <= (double)right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double)left - (double)right;
            //chapter 7 challenge 2 starts here
            case PLUS:
                if (left instanceof Double && right instanceof Double) {
                    return (double)left + (double)right;
                }
                if (left instanceof String || right instanceof String) {
                    return stringify(left) + stringify(right);
                }
                throw new RuntimeError(expr.operator,
                "Operands must be two numbers or two strings.");
            // end
            //chapter 7 challenge 3 starts here
            case SLASH:
                if( (double)right == 0) {
                throw new RuntimeError(expr.operator, "Division by zero.");
                }
                checkNumberOperands(expr.operator, left, right);
                return (double)left / (double)right;
            // end
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) { 
            arguments.add(evaluate(argument));
        }

        if (!(callee instanceof LoxCallable)) {
        throw new RuntimeError(expr.paren,
            "Can only call functions and classes.");
        }

        LoxCallable function = (LoxCallable)callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " +
                function.arity() + " arguments but got " +
                arguments.size() + ".");
        }

        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object object = evaluate(expr.object);
        if (object instanceof LoxInstance) {
        return ((LoxInstance) object).get(expr.name);
        }

        throw new RuntimeError(expr.name,
            "Only instances have properties.");
    }


    void interpret(List<Stmt> statements) {
        try {
        for (Stmt statement : statements) {
            execute(statement);
        }
        } catch (RuntimeError error) {
        Lox.runtimeError(error);
        }
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        LoxFunction function = new LoxFunction(stmt.name.lexeme, stmt.function,
            environment, false);

        if (environment == null) {
            globals.put(stmt.name.lexeme, function);
        } else {
            environment.define(function);
        }
        return null;
    }

    @Override
    public Object visitFunctionExpr(Expr.Function expr) {
        return new LoxFunction(null, expr, environment, false);
    }

    private void define(Token name, Object value) {
        if (environment != null) {
        environment.define(value);
        } else {
        globals.put(name.lexeme, value);
        }
    }
}