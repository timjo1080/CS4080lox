package lox;

import lox.Expr.Assign;
import lox.Expr.Logical;
import lox.Expr.Variable;

class RPNConverter implements Expr.Visitor<String> {
    String print(Expr expr) {
        return expr.accept(this);
    }

    public String visitConditionalExpr(Expr.Conditional expr) {
        return expr.condition.accept(this) + " " + expr.thenBranch.accept(this) + " " + expr.elseBranch.accept(this) + " ?";
    }
    
    /* challenge 3 starts here */

    /* for binary groupings e.g. 1 + 2 */
    public String visitBinaryExpr(Expr.Binary expr) {
        return expr.left.accept(this) + " " + expr.right.accept(this) + " " + expr.operator.lexeme;
    }

    /* for grouping expressions e.g. (1 + 2) */
    public String visitGroupingExpr(Expr.Grouping expr) {
        return expr.expression.accept(this);
    }

    /* for literal values e.g. 1, "hello" */
    public String visitLiteralExpr(Expr.Literal expr) {
        return expr.value.toString();
    }

    /* for unary expressions e.g. !true */
    public String visitUnaryExpr(Expr.Unary expr) {
        return expr.right.accept(this) + " " + expr.operator.lexeme;
    }

    public static void main(String[] args) {
        Expr expression = new Expr.Binary(
            new Expr.Grouping(new Expr.Binary(new Expr.Literal(1), new Token(TokenType.PLUS, "+", null, 1), new Expr.Literal(2))),
            new Token(TokenType.STAR, "*", null, 1),
            new Expr.Grouping(
                new Expr.Binary(new Expr.Literal(4), new Token(TokenType.MINUS, "-", null, 1), new Expr.Literal(3))));

        System.out.println(new RPNConverter().print(expression));
    }

    @Override
    public String visitAssignExpr(Assign expr) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visitAssignExpr'");
    }

    @Override
    public String visitVariableExpr(Variable expr) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visitVariableExpr'");
    }

    @Override
    public String visitLogicalExpr(Logical expr) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visitLogicalExpr'");
    }
}