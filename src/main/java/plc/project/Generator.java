package plc.project;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.BigInteger;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        // create a "class Main{"
        print("public class Main {");
        // blank line
        newline(0);
        newline(++indent);

        //    declare fields -> properties
        if(!ast.getFields().isEmpty()) {
            for (int i = 0; i < ast.getFields().size(); i++) {
                if (i != 0)
                    newline(indent);  // Newline before each field except first
                visit(ast.getFields().get(i));
            }
            newline(0);
            newline(indent);
        }

        //    declare "public static void main(String[] args){
        print("public static void main(String[] args) {");
        newline(++indent);

        //                  System.exit(new Main().main();
        //             }"
        print ("System.exit(new Main().main());");
        newline(--indent);
        print("}");

        newline(0);

        //    declare each of our functions -> methods
        //    one of our functions -> methods is called main()
        newline(indent);

        for (int i = 0; i < ast.getMethods().size(); i++) {
            if (i != 0) {
                newline(0);
                newline(indent);
            }
            visit(ast.getMethods().get(i));
        }

        newline(0);

        // print "}" to close class Main
        newline(--indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        // if constant
        if (ast.getConstant())
            print("final ");


        // print jvm type and name
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());

        // if there is a value declared
        if (ast.getValue().isPresent())
            print(" = ", ast.getValue().get());

        print(";");
        //newline(indent);

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        print(ast.getFunction().getType().getJvmName(), " ", ast.getFunction().getJvmName(), "(");

        // print parameters
        if(!ast.getParameters().isEmpty()){
            for(int i = 0; i < ast.getParameters().size(); i++) {
                // print type
                print(ast.getFunction().getParameterTypes().get(i).getJvmName()," ");
                // print variable name
                print(ast.getParameters().get(i));
                if(i != ast.getParameters().size() - 1)
                    print(", ");
            }
        }
        print(") {");

        // print statements
        if(!ast.getStatements().isEmpty()) {
            newline(++indent);
            for(int i = 0; i < ast.getStatements().size(); i++) {
                if(i != 0)
                    newline(indent);
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }

        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        print(ast.getExpression(), ";");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {

        print(ast.getVariable().getType().getJvmName(),
                " ",
                ast.getVariable().getJvmName());

        if(ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }

        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(), ";");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        // print if statements
        print("if (", ast.getCondition(), ") {");

        if (!ast.getThenStatements().isEmpty()) {
            newline(++indent);
            for (int i = 0; i < ast.getThenStatements().size(); i++) {
                // skip first newline
                if (i != 0)
                    newline(indent);
                print(ast.getThenStatements().get(i));
            }
            newline(--indent);
        }

        // print else statements
        if(!ast.getElseStatements().isEmpty()) {
            print("} else {");
            newline(++indent);
            for(int i = 0; i < ast.getElseStatements().size(); i++) {
                if (i != 0)
                    newline(indent);
                print(ast.getElseStatements().get(i));
            }
            newline(--indent);
            print("}");
        }
        else
            print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.For ast) {

        print("for ( ");

        // initialization
        if(ast.getInitialization() != null)
            print(ast.getInitialization(), " ");
        else
            print("; ");

        // condition
        print(ast.getCondition(), ";");

        // increment
        if(ast.getIncrement() != null) {
            Ast.Statement.Assignment incr = (Ast.Statement.Assignment) ast.getIncrement();
            print(" ", incr.getReceiver(), " = ", incr.getValue());
        }

        print(" ) {");

        // print statements
        if(!ast.getStatements().isEmpty()) {
            newline(++indent);
            for (int i = 0; i < ast.getStatements().size(); i++) {
                if (i != 0)
                    newline(indent);
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }

        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        print("while (", ast.getCondition(), ") {");

        if(!ast.getStatements().isEmpty()) {
            newline(++indent);
            for (int i = 0; i <ast.getStatements().size(); i++) {
                if(i != 0)
                    newline(indent);
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }

        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ", ast.getValue(), ";");

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        Object lit = ast.getLiteral();

        // print single or double quotes for string and char
        if(lit instanceof String)
            print("\"", ast.getLiteral(), "\"");
        else if (lit instanceof Character)
            print("'", ast.getLiteral(), "'");
        else
            print(ast.getLiteral());

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(", ast.getExpression(), ")");

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        print(ast.getLeft(), " ", ast.getOperator(), " ", ast.getRight());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        // check for receiver and print period
        if(ast.getReceiver().isPresent())
            print(ast.getReceiver().get(), ".");

        print(ast.getVariable().getJvmName());

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        // check for receiver and print period
        if(ast.getReceiver().isPresent())
            print(ast.getReceiver().get(), ".");

        print(ast.getFunction().getJvmName(), "(");

        if(!ast.getArguments().isEmpty()){
            for(int i = 0; i < ast.getArguments().size(); i++) {
                print(ast.getArguments().get(i));
                if (i < ast.getArguments().size() - 1)
                    print(", ");
            }
        }
        print(")");

        return null;
    }
}
