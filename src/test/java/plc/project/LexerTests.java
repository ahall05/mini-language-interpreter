package plc.project;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class LexerTests {

    @ParameterizedTest
    @MethodSource
    void testIdentifier(String test, String input, boolean success) {
        test(input, Token.Type.IDENTIFIER, success);
    }

    private static Stream<Arguments> testIdentifier() {
        return Stream.of(
                //Arguments.of("Alphabetic", "get Name", true),
                Arguments.of("Alphabetic", "getName", true),
                Arguments.of("Alphanumeric", "thelegend27", true),
                Arguments.of("Leading Hyphen", "-five", false),
                Arguments.of("Leading Digit", "1fish2fish3fishbluefish", false),
                Arguments.of("single character", "x", true),
                Arguments.of("Alphabetic", "abc", true),
                Arguments.of("Alphanumeric", "abc123", true),
                Arguments.of("Hyphens", "a-b-c", true),
                Arguments.of("Only Underscores", "___", true),
                Arguments.of("Leading Underscore", "_abc", true),
                Arguments.of("Capitals", "ABC", true),
                Arguments.of("Short Identifier", "a", true),
                Arguments.of("Long Identifier", "abcdefghijklmnopqrstuvwxyz012346789_-", true),
                Arguments.of("And", "AND", true),
                Arguments.of("Or", "OR", true)

        );
    }

    @ParameterizedTest
    @MethodSource
    void testInteger(String test, String input, boolean success) {
        test(input, Token.Type.INTEGER, success);
    }

    private static Stream<Arguments> testInteger() {
        return Stream.of(
                Arguments.of("Single Digit", "1", true),
                Arguments.of("Multiple Digits", "12345", true),
                Arguments.of("Negative", "-1", true),
                Arguments.of("Leading Zero", "01", false),
                Arguments.of("decimal", "123.45", false),
                Arguments.of("comma separated", "1,234", false),
                Arguments.of("leading zeros", "001", false),
                Arguments.of("Negative", "-1", true)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDecimal(String test, String input, boolean success) {
        test(input, Token.Type.DECIMAL, success);
    }

    private static Stream<Arguments> testDecimal() {
        return Stream.of(
                Arguments.of("Multiple Digits", "123.456", true),
                Arguments.of("Negative Decimal", "-1.0", true),
                Arguments.of("Trailing Decimal", "1.", false),
                Arguments.of("Leading Decimal", ".5", false),
                Arguments.of("single digit", "1", false),
                Arguments.of("trailing zeros", "1.000", true),
                Arguments.of("double decimal", "1..0", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testCharacter(String test, String input, boolean success) {
        test(input, Token.Type.CHARACTER, success);
    }

    private static Stream<Arguments> testCharacter() {
        return Stream.of(
                Arguments.of("Alphabetic", "\'c\'", true),
                Arguments.of("Newline Escape", "\'\\n\'", true),
                Arguments.of("Empty", "\'\'", false),
                Arguments.of("Multiple", "\'abc\'", false),
                Arguments.of("unterminated", "\'", false),
                Arguments.of("newline", "'␊'", true),
                Arguments.of("Empty", "''", false),
                Arguments.of("Multiple", "'abc'", false),
                Arguments.of("Unterminated Empty Quote", "'", false),
                Arguments.of("Alphabetic", "'c'", true),
                Arguments.of("Digit", "'1'", true),
                Arguments.of("Unicode", "'ρ'", true),
                Arguments.of("Space", "' '", true),
                Arguments.of("Single Quote Escape", "'\\''", true),
                Arguments.of("Backslash Escape", "'\\\\'", true)

        );
    }

    @ParameterizedTest
    @MethodSource
    void testString(String test, String input, boolean success) {
        test(input, Token.Type.STRING, success);
    }

    private static Stream<Arguments> testString() {
        return Stream.of(
                Arguments.of("Empty", "\"\"", true),
                Arguments.of("Alphabetic", "\"abc\"", true),
                Arguments.of("Newline Escape", "\"Hello,\\nWorld\"", true),
                Arguments.of("Unterminated", "\"unterminated", false),
                Arguments.of("Invalid Escape", "\"invalid\\escape\"", false),
                Arguments.of("symbols", "\"!@#$%^&*()\"", true),
                Arguments.of("newline unterminated", "\"unterminated\n\"", false),
                Arguments.of("Empty", "\"\"", true),
                Arguments.of("Single Character", "\"c\"", true),
                Arguments.of("Alphabetic", "\"abc\"", true),
                Arguments.of("Numeric", "\"123\"", true),
                Arguments.of("Symbols", "\"!@#$%^&*\"", true),
                Arguments.of("Unicode", "\"ρ★⚡\"", true),
                Arguments.of("Whitespace", "\" ␈␉\"", true), // space, backspace, tab
                Arguments.of("Escape", "\"Hello, \\nWorld!\"", true),
                Arguments.of("Alphabetic Escapes", "\"a\\bcdefghijklm\\nopq\\rs\\tuvwxyz\"", true),
                Arguments.of("Special Escapes", "\"sq\\'dq\\\"bs\\\\\"", true),
                Arguments.of("Invalid Escape", "\"abc\\0123\"", false),     // octal-style, not supported
                Arguments.of("Unicode Escapes", "\"a\\u0000b\\u12ABc\"", false), // Java-style  escapes not supported
                Arguments.of("Unterminated", "\"unterminated", false),
                Arguments.of("Unterminated Newline", "\"unterminated\n\"", false),
                Arguments.of("Unterminated Empty Quote", "\"", false)

        );
    }

    @ParameterizedTest
    @MethodSource
    void testOperator(String test, String input, boolean success) {
        //this test requires our lex() method, since that's where whitespace is handled.
        test(input, Arrays.asList(new Token(Token.Type.OPERATOR, input, 0)), success);
    }

    private static Stream<Arguments> testOperator() {
        return Stream.of(
                Arguments.of("Character", "(", true),
                Arguments.of("Comparison", "!=", true),
                Arguments.of("Space", " ", false),
                Arguments.of("Tab", "\t", false),
                Arguments.of("symbol", "$", false),
                Arguments.of("plus sign", "+", true),
                Arguments.of("Remainder", "%", true),
                Arguments.of("Unicode", "ρ", true),
                Arguments.of("Greater Than", ">", true),
                Arguments.of("Not Equals", "!=", true),
                Arguments.of("Less Than Equals", "<=", true),
                Arguments.of("Plus", "+", true),
                Arguments.of("Hyphen", "-", true),
                Arguments.of("Space", " ", true),
                Arguments.of("Tab", "\t", true),
                Arguments.of("Form Feed", "\f", true)

        );
    }

    @ParameterizedTest
    @MethodSource
    void testExamples(String test, String input, List<Token> expected) {
        test(input, expected, true);
    }

    private static Stream<Arguments> testExamples() {
        return Stream.of(
                Arguments.of("Example 1", "LET x = 5;", Arrays.asList(
                        new Token(Token.Type.IDENTIFIER, "LET", 0),
                        new Token(Token.Type.IDENTIFIER, "x", 4),
                        new Token(Token.Type.OPERATOR, "=", 6),
                        new Token(Token.Type.INTEGER, "5", 8),
                        new Token(Token.Type.OPERATOR, ";", 9)
                )),
                Arguments.of("Example 2", "print(\"Hello, World!\");", Arrays.asList(
                        new Token(Token.Type.IDENTIFIER, "print", 0),
                        new Token(Token.Type.OPERATOR, "(", 5),
                        new Token(Token.Type.STRING, "\"Hello, World!\"", 6),
                        new Token(Token.Type.OPERATOR, ")", 21),
                        new Token(Token.Type.OPERATOR, ";", 22)
                ))
                ,
                // 1) Empty input → no tokens
                Arguments.of("Empty", "", Arrays.asList()),

                // 2) All Types:  abc 123 456.789 'c' "string" %
                Arguments.of("All Types", "abc 123 456.789 'c' \"string\" %",
                        Arrays.asList(
                                new Token(Token.Type.IDENTIFIER, "abc", 0),
                                new Token(Token.Type.INTEGER, "123", 4),
                                new Token(Token.Type.DECIMAL, "456.789", 8),
                                new Token(Token.Type.CHARACTER, "'c'", 16),
                                new Token(Token.Type.STRING, "\"string\"", 20),
                                new Token(Token.Type.OPERATOR, "%", 29)
                        )
                ),

                // 3) Leading Digit:  1fish2fish3fishbluefish
                //    INTEGER("1") then IDENTIFIER("fish2fish3fishbluefish")
                Arguments.of("Leading Digit", "1fish2fish3fishbluefish",
                        Arrays.asList(
                                new Token(Token.Type.INTEGER, "1", 0),
                                new Token(Token.Type.IDENTIFIER, "fish2fish3fishbluefish", 1)
                        )
                ),

                // 4) Leading Decimal:  .5  → "." operator then INTEGER("5")
                Arguments.of("Leading Decimal", ".5",
                        Arrays.asList(
                                new Token(Token.Type.OPERATOR, ".", 0),
                                new Token(Token.Type.INTEGER, "5", 1)
                        )
                ),

                // 5) Double Decimal:  1..0  → INTEGER("1"), ".", ".", INTEGER("0")
                Arguments.of("Double Decimal", "1..0",
                        Arrays.asList(
                                new Token(Token.Type.INTEGER, "1", 0),
                                new Token(Token.Type.OPERATOR, ".", 1),
                                new Token(Token.Type.OPERATOR, ".", 2),
                                new Token(Token.Type.INTEGER, "0", 3)
                        )
                ),

                // 6) Multiple Decimals:  1.2.3  → DECIMAL("1.2"), ".", INTEGER("3")
                Arguments.of("Multiple Decimals", "1.2.3",
                        Arrays.asList(
                                new Token(Token.Type.DECIMAL, "1.2", 0),
                                new Token(Token.Type.OPERATOR, ".", 3),
                                new Token(Token.Type.INTEGER, "3", 4)
                        )
                ),

                // 7) Number Method:  1.toString()
                Arguments.of("Number Method", "1.toString()",
                        Arrays.asList(
                                new Token(Token.Type.INTEGER, "1", 0),
                                new Token(Token.Type.OPERATOR, ".", 1),
                                new Token(Token.Type.IDENTIFIER, "toString", 2),
                                new Token(Token.Type.OPERATOR, "(", 10),
                                new Token(Token.Type.OPERATOR, ")", 11)
                        )
                ),

                // 8) Not Subtraction:  15-10  → INTEGER("15"), "-", INTEGER("10")
                Arguments.of("Not Subtraction", "15-10",
                        Arrays.asList(
                                new Token(Token.Type.INTEGER, "15", 0),
                                new Token(Token.Type.OPERATOR, "-", 2),
                                new Token(Token.Type.INTEGER, "10", 3)
                        )
                ),

                // 9) Inner String:  abc"str"123
                Arguments.of("Inner String", "abc\"str\"123",
                        Arrays.asList(
                                new Token(Token.Type.IDENTIFIER, "abc", 0),
                                new Token(Token.Type.STRING, "\"str\"", 3),
                                new Token(Token.Type.INTEGER, "123", 8)
                        )
                ),

                // 10) Weird Quotes:  '"'string"'"
                //     CHARACTER('"' ), IDENTIFIER(string), STRING("\"'\"")
                Arguments.of("Weird Quotes", "'\"'string\"'\"",
                        Arrays.asList(
                                new Token(Token.Type.CHARACTER, "'\"'", 0),
                                new Token(Token.Type.IDENTIFIER, "string", 3),
                                new Token(Token.Type.STRING, "\"'\"", 9)
                        )
                ),

                // 11) All The Quotes:  """"""""""
                //     Five empty strings back-to-back (10 quotes)
                Arguments.of("All The Quotes", "\"\"\"\"\"\"\"\"\"\"",
                        Arrays.asList(
                                new Token(Token.Type.STRING, "\"\"", 0),
                                new Token(Token.Type.STRING, "\"\"", 2),
                                new Token(Token.Type.STRING, "\"\"", 4),
                                new Token(Token.Type.STRING, "\"\"", 6),
                                new Token(Token.Type.STRING, "\"\"", 8)
                        )
                ),

                // 12) Equals Combinations:  !====  → "!=" "==" "="
                Arguments.of("Equals Combinations", "!====",
                        Arrays.asList(
                                new Token(Token.Type.OPERATOR, "!=", 0),
                                new Token(Token.Type.OPERATOR, "==", 2),
                                new Token(Token.Type.OPERATOR, "=", 4)
                        )
                ),

                // 13) Spaceship Operator:  <=>  → "<=" then ">"
                Arguments.of("Spaceship Operator", "<=>",
                        Arrays.asList(
                                new Token(Token.Type.OPERATOR, "<=", 0),
                                new Token(Token.Type.OPERATOR, ">", 2)
                        )
                ),

                // 14) Right Bitshift:  >>  → ">" then ">"
                Arguments.of("Right Bitshift", ">>",
                        Arrays.asList(
                                new Token(Token.Type.OPERATOR, ">", 0),
                                new Token(Token.Type.OPERATOR, ">", 1)
                        )
                ),

                // 15) Null Character:  null\0
                // In Java, use "\0" to embed NUL. It’s not whitespace per your lexer,
                // so it becomes a single-character OPERATOR after the identifier.
                Arguments.of("Null Character", "null\0",
                        Arrays.asList(
                                new Token(Token.Type.IDENTIFIER, "null", 0),
                                new Token(Token.Type.OPERATOR, "\0", 4)
                        )
                ),

                // 16) Operator Integer:  +0  → "+" then INTEGER("0")
                Arguments.of("Operator Integer", "+0",
                        Arrays.asList(
                                new Token(Token.Type.OPERATOR, "+", 0),
                                new Token(Token.Type.INTEGER, "0", 1)
                        )
                )
        );
    }

    @Test
    void testException() {
        ParseException exception = Assertions.assertThrows(ParseException.class,
                () -> new Lexer("\"unterminated").lex());
        Assertions.assertEquals(13, exception.getIndex());
    }

    /**
     * Tests that lexing the input through {@link Lexer#lexToken()} produces a
     * single token with the expected type and literal matching the input.
     */
    private static void test(String input, Token.Type expected, boolean success) {
        try {
            if (success) {
                Assertions.assertEquals(new Token(expected, input, 0), new Lexer(input).lexToken());
            } else {
                Assertions.assertNotEquals(new Token(expected, input, 0), new Lexer(input).lexToken());
            }
        } catch (ParseException e) {
            Assertions.assertFalse(success, e.getMessage());
        }
    }

    /**
     * Tests that lexing the input through {@link Lexer#lex()} matches the
     * expected token list.
     */
    private static void test(String input, List<Token> expected, boolean success) {
        try {
            if (success) {
                Assertions.assertEquals(expected, new Lexer(input).lex());
            } else {
                Assertions.assertNotEquals(expected, new Lexer(input).lex());
            }
        } catch (ParseException e) {
            Assertions.assertFalse(success, e.getMessage());
        }
    }

}
