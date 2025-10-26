package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling those functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {

        this.tokens = new TokenStream(tokens);

    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        List<Ast.Field> fields = new ArrayList<>();
        List<Ast.Method> methods = new ArrayList<>();

        while (peek("LET")) {
            fields.add(parseField());
        }
        while (peek("DEF")) {
            methods.add(parseMethod());
        }
        if(peek("LET"))
            throw new ParseException("Field follows Method", tokens.get(0).getIndex());

        return new Ast.Source(fields, methods);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException {

        match("LET");

        boolean constant = false;
        if(match("CONST"))
            constant = true;

        String name;
        if(match(Token.Type.IDENTIFIER))
            name = checkHas().getLiteral();
        else
            throw new ParseException("Expected identifier", tokens.get(0).getIndex());

        Optional<Ast.Expression> expr = Optional.empty();
        if (match("=")) {
            expr = Optional.of(parseExpression());
        }

        if(!match(";"))
            throw new ParseException("Missing ';'", tokens.get(0).getIndex());

        return new Ast.Field(name, constant, expr);
    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException {
        match("DEF");

        // get method name
        String name;
        if(match(Token.Type.IDENTIFIER))
            name = checkHas().getLiteral();
        else
            throw new ParseException("Expected identifier", tokens.get(0).getIndex());

        // check for (
        if(!match("("))
            throw new ParseException("Expected '('", tokens.get(0).getIndex());

        // get parameters
        List<String> parameters = new ArrayList<>();
        if(peek(Token.Type.IDENTIFIER))
        {
            do{
                parameters.add(checkHas().getLiteral());
            }while(peek(","));
        }

        // check for )
        if(!match(")"))
            throw new ParseException("Expected ')'", tokens.get(0).getIndex());

        if(!match("DO"))
            throw new ParseException("Expected 'DO'", tokens.get(0).getIndex());

        // get statement(s)
        List<Ast.Statement> statements = new ArrayList<Ast.Statement>();
        while (!peek("END") && tokens.has(0))
        {
            statements.add(parseStatement());
        }

        // check for END
        if(!match("END"))
            throw new ParseException("Missing 'END'", tokens.get(-1).getIndex());

        return new Ast.Method(name, parameters, statements);
    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, for, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Statement parseStatement() throws ParseException {

        if(peek("LET"))
        {
            return parseDeclarationStatement();
        }
        else if (peek("IF"))
        {
            return parseIfStatement();
        }
        else if (peek("FOR"))
        {
            return parseForStatement();
        }
        else if(peek("WHILE"))
        {
            return  parseWhileStatement();
        }
        else if(peek("RETURN"))
        {
            return parseReturnStatement();
        }
        else
        {
            Ast.Expression expr = parseExpression();

            // if there is an equal sign, it's an assignment
            if (match("=")) {
                Ast.Expression right = parseExpression();
                if (!match(";"))
                    throw new ParseException("Missing ';'", tokens.get(0).getIndex());

                return new Ast.Statement.Assignment(expr, right);
            }

            // else it's an expression
            if (!match(";"))
                throw new ParseException("Missing ';'", tokens.get(-1).getIndex());

            return new Ast.Statement.Expression(expr);
        }

    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Statement.Declaration parseDeclarationStatement() throws ParseException {
        match("LET");

        if(!match(Token.Type.IDENTIFIER))
        {
            throw new ParseException("Expected identifier", tokens.get(0).getIndex());
        }

        String name = tokens.get(-1).getLiteral();
        Optional<Ast.Expression> value = Optional.empty();

        if(match("="))
        {
            value = Optional.of(parseExpression());
        }
        if (!match(";"))
            throw new ParseException("Missing ';'", tokens.get(0).getIndex());


        return new Ast.Statement.Declaration(name, value);
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Statement.If parseIfStatement() throws ParseException {
        match("IF");
        Ast.Expression condition = parseExpression();

        List<Ast.Statement> doStatements = new ArrayList<Ast.Statement>();
        if(match("DO"))
        {
            while(!peek("ELSE") && !peek("END"))
                doStatements.add(parseStatement());
        }
        else
            throw new ParseException("Expected \"DO\"", tokens.get(0).getIndex());

        List<Ast.Statement> elseStatements = new ArrayList<Ast.Statement>();
        if(match("ELSE"))
        {
            while(!peek("END"))
                elseStatements.add(parseStatement());
        }

        if(!match("END"))
            throw new ParseException("Missing END", tokens.get(0).getIndex());

        return new Ast.Statement.If(condition, doStatements, elseStatements);
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Statement.For parseForStatement() throws ParseException {
        match("FOR");

        if(!match("("))
            throw new ParseException("Missing '('", tokens.get(0).getIndex());

        // get initialization
        Ast.Statement.Assignment initialization = null;
        if(peek(Token.Type.IDENTIFIER))
        {
            Ast.Expression reciever = parseExpression();
            if(!match("="))
                throw new ParseException("Missing '='", tokens.get(0).getIndex());
            Ast.Expression value = parseExpression();
            initialization = new Ast.Statement.Assignment(reciever, value);
        }

        // check for semicolon
        if(!match(";"))
            throw new ParseException("Missing ';'", tokens.get(0).getIndex());

        // get condition
        Ast.Expression condition = parseExpression();

        if(!match(";"))
            throw new ParseException("Missing ';'", tokens.get(0).getIndex());


        // get increment
        Ast.Statement increment = null;

        if(peek(Token.Type.IDENTIFIER))
        {
            Ast.Expression reciever = parseExpression();
            if(!match("="))
                throw new ParseException("Missing '='", tokens.get(0).getIndex());
            Ast.Expression value = parseExpression();
            increment = new Ast.Statement.Assignment(reciever, value);
        }


        // check closing parenthesis
        if(!match(")"))
            throw new ParseException("Missing ')'", tokens.get(0).getIndex());

        // get statement(s)
        List<Ast.Statement> statements = new ArrayList<Ast.Statement>();
        while (!peek("END") && tokens.has(0))
        {
            statements.add(parseStatement());
        }

        if(!match("END"))
            throw new ParseException("Missing 'END'", tokens.get(-1).getIndex());

        return new Ast.Statement.For(initialization, condition, increment, statements);
    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Statement.While parseWhileStatement() throws ParseException {
        match("WHILE");
        Ast.Expression condition = parseExpression();

        if(!match("DO"))
            throw new ParseException("Missing 'DO'", tokens.get(0).getIndex());

        // get statement(s)
        List<Ast.Statement> statements = new ArrayList<Ast.Statement>();
        while (!peek("END") && tokens.has(0))
        {
            statements.add(parseStatement());
        }

        if(!match("END"))
            throw new ParseException("Missing 'END'", tokens.get(-1).getIndex());
        return new Ast.Statement.While(condition, statements);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Statement.Return parseReturnStatement() throws ParseException {
       match("RETURN");

       Ast.Expression expr = parseExpression();

       if(!match(";"))
           throw new ParseException("Missing ';'", tokens.get(0).getIndex());
       return new Ast.Statement.Return(expr);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expression parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expression parseLogicalExpression() throws ParseException {
        Ast.Expression expr = parseEqualityExpression();

        while(match("AND") || match("OR"))
        {
            // store operator in temp string
            String operator = checkHas().getLiteral();

            // check for missing RHS
            if (!potentialStartExpression()) {
                throw new ParseException("Expected expression after '" + operator + "'.", checkHas().getIndex());
            }

            // get right operand
            Ast.Expression right = parseEqualityExpression();

            // build node
            expr = new Ast.Expression.Binary(operator, expr, right);
        }

        return expr;
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expression parseEqualityExpression() throws ParseException {
        Ast.Expression expr = parseAdditiveExpression();

        while(match("<") || match("<=") || match(">") || match(">=") || match("==") || match("!="))
        {
            // store operator in temp string
            String operator = checkHas().getLiteral();

            // check for missing RHS
            if (!potentialStartExpression()) {
                throw new ParseException("Expected expression after '" + operator + "'.", checkHas().getIndex());
            }

            // get right operand
            Ast.Expression right = parseAdditiveExpression();

            // build node
            expr = new Ast.Expression.Binary(operator, expr, right);
        }

        return expr;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expression parseAdditiveExpression() throws ParseException {
        Ast.Expression expr = parseMultiplicativeExpression();

        while (match("+") || match("-"))
        {
            // store operator in temp string
            String operator = checkHas().getLiteral();

            // check for missing RHS
            if (!potentialStartExpression()) {
                throw new ParseException("Expected expression after '" + operator + "'.", checkHas().getIndex());
            }

            // get right operand
            Ast.Expression right = parseMultiplicativeExpression();

            // build node
            expr = new Ast.Expression.Binary(operator, expr, right);

        }
        return expr;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expression parseMultiplicativeExpression() throws ParseException {
        Ast.Expression expr = parseSecondaryExpression();

        while (match("*") || match("/"))
        {
            // store operator in temp string
            String operator = checkHas().getLiteral();

            // check for missing RHS
            if (!potentialStartExpression()) {
                throw new ParseException("Expected expression after '" + operator + "'.", checkHas().getIndex());
            }

            // get right operand
            Ast.Expression right = parseSecondaryExpression();

            // build node
            expr = new Ast.Expression.Binary(operator, expr, right);

        }
        return expr;
    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    public Ast.Expression parseSecondaryExpression() throws ParseException {

        Ast.Expression expr = parsePrimaryExpression();

        while(match("."))
        {
            // check for identifier after period
            if(!peek(Token.Type.IDENTIFIER))
                throw new ParseException("Expected identifier after \".\"", checkHas().getIndex());

            // consume and store identifier literal
            match(Token.Type.IDENTIFIER);
            String label = checkHas().getLiteral();


            // check for and store parenthesis
            if(match("("))
            {
                // check for empty parentheses
                if (match(")"))
                {
                    // if empty parentheses, break out of loop
                    expr = new Ast.Expression.Function(Optional.of(expr), label, Arrays.asList());
                }
                // perform the same actions as in primaryExpression, but assign expr a Function with a receiver
                else
                {
                    List<Ast.Expression> args = new ArrayList<>();
                    // add arguments while comma is present
                    do {
                        args.add(parseExpression());
                    } while (match(","));

                    // if closing parenthesis present, create Ast Function
                    if (match(")"))
                        expr = new Ast.Expression.Function(Optional.of(expr), label, args);
                    else
                        throw new ParseException("Expected closing parenthesis", tokens.get(0).getIndex());
                }
            }
            else
                // assign expr an Access with receiver
                expr = new Ast.Expression.Access(Optional.of(expr), label);
        }

        return expr;
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expression parsePrimaryExpression() throws ParseException {

        // check for boolean values
        if(match("TRUE"))
        {
            return new Ast.Expression.Literal(Boolean.TRUE);
        }
        else if (match("FALSE"))
        {
            return new Ast.Expression.Literal(Boolean.FALSE);
        }
        else if (match("NIL"))
        {
            return new Ast.Expression.Literal(null);
        }
        else if(match(Token.Type.INTEGER))
        {
            Token current = checkHas();

            return new Ast.Expression.Literal(new BigInteger(current.getLiteral()));
        }
        else if(match(Token.Type.DECIMAL))
        {
            Token current = checkHas();

            return new Ast.Expression.Literal(new BigDecimal(current.getLiteral()));
        }
        else if (match(Token.Type.CHARACTER))
        {
            String tempLit = checkHas().getLiteral();

            // strip single quotes
            tempLit = tempLit.substring(1, tempLit.length() - 1);

            // replace escape sequences
            tempLit = tempLit.replace("\\b", "\b")
                             .replace("\\n", "\n")
                             .replace("\\r", "\r")
                             .replace("\\t", "\t")
                             .replace("\\'", "\'")
                             .replace("\\\"", "\"")
                             .replace("\\\\", "\\");

            // match character and return
            match(Token.Type.CHARACTER);
            Character ch = tempLit.charAt(0);

            return new Ast.Expression.Literal(ch);
        }
        else if(match(Token.Type.STRING))
        {
            String tempLit = checkHas().getLiteral();

            // strip double quotes
            tempLit = tempLit.substring(1, tempLit.length() - 1);
            // replace escape sequences
            tempLit = tempLit.replace("\\b", "\b")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\'", "\'")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");

            return new Ast.Expression.Literal(tempLit);
        }
        else if(match("("))
        {
            Ast.Expression expr = parseExpression();

            if(!match(")"))
            {
                throw new ParseException("Expected closing parenthesis", tokens.get(-1).getIndex()); // index 0 because ot is not consumed
            }
            return new Ast.Expression.Group(expr);
        }
        else if(match(Token.Type.IDENTIFIER))
        {
            Token current = checkHas();
            String label = current.getLiteral();

            if(match("("))
            {
                // if there are no arguments
                if (match(")"))
                    return new Ast.Expression.Function(Optional.empty(), label, Arrays.asList());
                else
                {
                    List<Ast.Expression> args = new ArrayList<>();
                    // add arguments while comma is present
                    do {
                        args.add(parseExpression());
                    } while (match(","));

                    if (match(")"))
                        return new Ast.Expression.Function(Optional.empty(), label, args);
                    else
                        throw new ParseException("Expected closing parenthesis", tokens.get(0).getIndex());

                }
            }

            return new Ast.Expression.Access(Optional.empty(), label);
        }
        else
            throw new ParseException("Invalid Primary Expression", tokens.get(0).getIndex());
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for (int i = 0; i < patterns.length; i++)
        {
            if (!tokens.has(i))
                return false;
            else if (patterns[i] instanceof Token.Type)
            {
                if(patterns[i] != tokens.get(i).getType())
                    return false;
            }
            else if (patterns[i] instanceof String)
            {
                if (!patterns[i].equals(tokens.get(i).getLiteral()))
                    return false;
            }
            else
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek =  peek(patterns);
        if(peek)
        {
            for (int i = 0; i < patterns.length; i++)
            {
                tokens.advance();
            }
        }

        return  peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }
    // boolean used to check if the RHS can be the start of an expression
    // used for error throwing if RHS is missing
    private boolean potentialStartExpression()
    {
        return peek("NIL") || peek("TRUE") || peek("FALSE")
                || peek(Token.Type.INTEGER) || peek(Token.Type.DECIMAL)
                || peek(Token.Type.CHARACTER) || peek(Token.Type.STRING)
                || peek("(") || peek(Token.Type.IDENTIFIER);
    }

    private Token checkHas() throws ParseException
    {
        if(!tokens.has(-1))
        {
            throw new ParseException("No previous token", tokens.get(0).getIndex());
        }

        return tokens.get(-1);

    }

}
