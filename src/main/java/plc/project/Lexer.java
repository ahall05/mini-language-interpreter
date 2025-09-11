package plc.project;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the invalid character.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are
 * helpers you need to use, they will make the implementation easier.
 */
public final class Lexer {

//            OPERATOR = Pattern.compile("([<>!=]=?|.)"),
//            WHITESPACE = Pattern.compile("[ \\b\\n\\r\\t]+");

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {

        List<Token> tokens = new ArrayList<>();

        while(chars.index != chars.input.length())
        {
            // check for whitespace
            while(peek("[ \\n\\r\\t\b]"))
                match("[ \\n\\r\\t]");

            // if at EOF, break
            if(chars.index >= chars.input.length())
                break;

            //reset token length
            chars.skip();

            // add token
            tokens.add(lexToken());
        }

        return tokens;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        if(peek("[A-Za-z_]"))
        {
            return lexIdentifier();
        }
        else if (peek("[+-]","[1-9]") || peek("[+-]","0","\\.","[0-9]") || peek("[0-9]"))
        {
            return lexNumber();
        }
        else if (peek("'"))
        {
            return lexCharacter();
        }
        else if (peek("\""))
        {
            return lexString();
        }
        else
        {
            return lexOperator();
        }
    }

    public Token lexIdentifier() {
        while(!peek("[^A-Za-z0-9_-]") && chars.index < chars.input.length() )
        {
            if(peek("[A-Za-z0-9_-]"))
                match("[A-Za-z0-9_-]");
            else
                throw new ParseException("Invalid identifier", chars.index);
        }

        return chars.emit(Token.Type.IDENTIFIER);
    }

    public Token lexNumber() {

        // consume sign only if it is a number to prevent consuming an operator
        if (peek("[+-]","[1-9]") || peek("[+-]","0","\\.","[0-9]"))
        {
            match("[+-]");
        }

        // first digit is zero, either 0 or 0.\d
        if (peek("0"))
        {
            // consume zero
            match("0");
            // if there is a decimal and at least one digit after
            if (peek("\\.","[0-9]"))
            {
                match("\\.", "[0-9]");

                while (peek("[0-9]"))
                    match("[0-9]");

                return chars.emit(Token.Type.DECIMAL);
            }
            // if this is reached, it's a single zero
            return chars.emit(Token.Type.INTEGER);
        }

        // first digit is non-zero
        if (peek("[1-9]"))
        {
            match("[1-9]");

            // consume remaining digits
            while (peek("[0-9]"))
                match("[0-9]");

            // check for digit following decimal
            if (peek("\\.","[0-9]"))
            {
                match("\\.","[0-9]");

                while (peek("[0-9]"))
                    match("[0-9]");

                // if this was reached, it is a decimal
                return chars.emit(Token.Type.DECIMAL);
            }
                // if this was reached, it was an integer
                return chars.emit(Token.Type.INTEGER);
        }
        // this should not be reached, if it is, throw exception
        throw new ParseException("Invalid number", chars.index);

    }

    public Token lexCharacter() {


        if(peek("'","[^'\\n\\r\\\\]","'"))
        {
            match("'","[^'\\n\\r\\\\]","'");
            return chars.emit(Token.Type.CHARACTER);
        }
        else if (peek("'", "\\\\", "[brnt'\"\\\\]", "'"))
        {
            match("'");
            lexEscape();
            match("'");
            return chars.emit(Token.Type.CHARACTER);
        }
        else
            throw new ParseException("Invalid character", chars.index);



    }

    public Token lexString() {

        // consume quote
        match("\"");

        while (!peek("\"") && chars.index < chars.input.length())
        {
            if(peek("\\n") || peek("\\r"))
                throw new ParseException("Invalid character", chars.index);
            // if there's a backslash, check for escape character
            if(peek("\\\\"))
                lexEscape();

            // else match on any character
            match(".");
        }
        // check for closing quote, if not present, throw exception
        if (peek("\""))
        {
            match("\"");
            return chars.emit(Token.Type.STRING);
        }

        throw new ParseException("Invalid string", chars.index);
    }

    public void lexEscape() {
        // check for backslash
        if (!peek("\\\\"))
            throw new ParseException("Expected backslash for escape", chars.index);
       match("\\\\");

        // check for valid escape
        if (!peek("[bnrt'\"\\\\]"))
            throw new ParseException("Invalid escape", chars.index);
        match("[bnrt'\"\\\\]");
    }

    public Token lexOperator() {
        if (peek("[=!<>]"))
        {
            match("[=!<>]");

            if(peek("="))
                match("=");
            else if (peek(" "))
                return chars.emit(Token.Type.OPERATOR);
            else
                throw new ParseException("Invalid operator", chars.index);
        }
        else
            match(".");

        return chars.emit(Token.Type.OPERATOR);
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    public boolean peek(String... patterns) {

        for (int i = 0; i < patterns.length; i++)
        {
            if ( !chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i]))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) {
        boolean peek = peek(patterns);
        if(peek)
        {
            for (int i = 0; i < patterns.length; i++)
            {
                chars.advance();
            }
        }

        return peek;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        public boolean has(int offset) {
            return index + offset < input.length();
        }

        public char get(int offset) {
            return input.charAt(index + offset);
        }

        public void advance() {
            index++;
            length++;
        }

        public void skip() {
            length = 0;
        }

        public Token emit(Token.Type type) {
            int start = index - length;
            skip();
            return new Token(type, input.substring(start, index), start);
        }

    }

}
