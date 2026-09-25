package lox;

import java.util.List;

import javafx.css.CssParser.ParseError;

import static lox.TokenType.*;

class Parser {
    private static class ParseError extends RuntimeException {}
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Expr parse() {
        try {
            return expression();
        } catch (ParseError error) {
            return null;
        }
    }

    private Expr expression() {
        return ternary();
    }

    // chapter 6 challenge 2 starts here
    private Expr ternary() {
        Expr expr = comma();
        if (match(QUESTION)) {
            Expr thenBranch = expression();
            consume(COLON, "Expect ':' after then branch of ternary expression.");
            Expr elseBranch = ternary();
            expr = new Expr.Conditional(expr, thenBranch, elseBranch);
        }
        return expr;
    }

    // chapter 6 challenge 1 ends here
    private Expr comma()
    {
        Expr expr = equality();


        while (match(COMMA)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparison() {
        Expr expr = term();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr term() {
        Expr expr = factor();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr factor() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // chapter 6 challenge 3 starts here
        if(match(COMMA))
        {
            error(previous(), "Missing Left-Hand Operand.");
            equality();
            return null;
        }
        if(match(QUESTION))
        {
            error(previous(), "Missing Left-Hand Operand.");
            ternary();
            return null;
        }
        if(match(COLON))
        {
            error(previous(), "Missing Left-Hand Operand.");
            ternary();
            return null;
        }
        if(match(EQUAL_EQUAL))
        {
            error(previous(), "Missing Left-Hand Operand.");
            equality();
            return null;
        }
        if(match(BANG_EQUAL))
        {
            error(previous(), "Missing Left-Hand Operand.");
            equality();
            return null;
        }
        if(match(GREATER))
        {
            error(previous(), "Missing Left-Hand Operand.");
            comparison();
            return null;
        }
        if(match(GREATER_EQUAL))
        {
            error(previous(), "Missing Left-Hand Operand.");
            comparison();
            return null;
        }
        if(match(LESS))
        {
            error(previous(), "Missing Left-Hand Operand.");
            comparison();
            return null;
        }
        if(match(LESS_EQUAL))
        {
            error(previous(), "Missing Left-Hand Operand.");
            comparison();
            return null;
        }
        if(match(PLUS))
        {
            error(previous(), "Missing Left-Hand Operand.");
            term();
            return null;
        }
        if(match(SLASH))
        {
            error(previous(), "Missing Left-Hand Operand.");
            factor();
            return null;
        }
        if(match(STAR))
        {
            error(previous(), "Missing Left-Hand Operand.");
            factor();
            return null;
        }


        throw error(peek(), "Expect expression.");
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }
    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }
}