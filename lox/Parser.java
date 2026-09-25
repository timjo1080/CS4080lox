package lox;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import javafx.css.CssParser.ParseError;

import static lox.TokenType.*;

class Parser {
    private static class ParseError extends RuntimeException {}
    private final List<Token> tokens;
    private int current = 0;
    private int loopDepth = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    //chapter 8 challenge 1 -- two passes: first, checks if expression is expression, then checks if expression is statement.
    public Object parseRepl() {
        if(match(VAR, PRINT, IF, WHILE, FOR, RETURN, FUN, CLASS, LEFT_BRACE))
        {
            current--;
            return parse();
        }
        
        int start = current;
        try
        {
            Expr expr = expression();
            if (isAtEnd()) return expr;
        } catch (ParseError error) {}
        
        Lox.hadError = false; // suppress error message for second pass
        current = start;

        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }

        return statements; 
    }

    private Expr expression() {
        return assignment();
    }

    private Stmt declaration() {
    try {
        if (match(VAR)) return varDeclaration();

        return statement();
        } catch (ParseError error) {
        synchronize();
        return null;
        }
    }

    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(BREAK)) return breakStatement();

        return expressionStatement();
    }

    private Stmt breakStatement() {
        if(loopDepth == 0)
        {
            throw error(previous(), "Cannot use 'break' outside of a loop.");
        }
        consume(SEMICOLON, "Expect ';' after 'break'.");
        return new Stmt.Break();
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");
        
        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");
        
        try{
            loopDepth++;
            Stmt body = statement();

            if (increment != null) {
            body = new Stmt.Block(
                Arrays.asList(
                    body,
                    new Stmt.Expression(increment)));
            }

            if (condition == null) condition = new Expr.Literal(true);
            body = new Stmt.While(condition, body);

            if (initializer != null) {
                body = new Stmt.Block(Arrays.asList(initializer, body));
            }
        return body;
        } finally {
            loopDepth--;
        }
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition."); 

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
        elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
        initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        try
        {
            loopDepth++;
            Stmt body = statement();

            return new Stmt.While(condition, body);
        } finally {
            loopDepth--;
        }
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
        statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr assignment() {
    Expr expr = or();

    if (match(EQUAL)) {
        Token equals = previous();
        Expr value = assignment();

        if (expr instanceof Expr.Variable) {
            Token name = ((Expr.Variable)expr).name;
            return new Expr.Assign(name, value);
        }

        error(equals, "Invalid assignment target."); 
        }

        return expr;
    }

    private Expr or() {
        Expr expr = and();

        while (match(OR)) {
        Token operator = previous();
        Expr right = and();
        expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr and() {
        Expr expr = equality();

        while (match(AND)) {
        Token operator = previous();
        Expr right = equality();
        expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
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

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
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