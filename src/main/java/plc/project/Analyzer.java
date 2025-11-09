package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {
    public Scope scope;
    private Ast.Method method;
    // for use in visit(return)
    private Environment.Type currentReturnType = Environment.Type.NIL;


    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        if(ast.getFields().isEmpty())
            throw new RuntimeException("missing fields");

        // visit fields
        for(Ast.Field field : ast.getFields())
            visit(field);

        // visit methods
        for(Ast.Method astMethod : ast.getMethods())
            visit(astMethod);

        // check for main function in scope
        Environment.Function mainFunc;
        try{
            mainFunc = scope.lookupFunction("main", 0);
        } catch (RuntimeException e) {
            throw new RuntimeException("Missing main function");
        }

        // check return type
        if(mainFunc.getReturnType() != Environment.Type.INTEGER)
            throw new RuntimeException("main function must have INTEGER return type");


        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        // visit initialization
        if(ast.getValue().isPresent())
            visit(ast.getValue().get());

        // get type
        Environment.Type type = Environment.getType(ast.getTypeName());
        // check type
        if(type == null)
            throw new RuntimeException("unknown type");

        // make sure const variable is initialized
        if(ast.getConstant() && ast.getValue().isEmpty())
            throw new RuntimeException("Const variable must be initialized");

        // check value type
        if (ast.getValue().isPresent())
            requireAssignable(type, ast.getValue().get().getType());

        // define and set within scope
        scope.defineVariable(ast.getName(), ast.getName(), type, ast.getConstant(), Environment.NIL);
        ast.setVariable(scope.lookupVariable(ast.getName()));

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        List <Environment.Type> params = new ArrayList<>();
        // handle parameters
        for (String paramType : ast.getParameterTypeNames()) {
            Environment.Type type = Environment.getType(paramType);
            if(type == null)
                throw new RuntimeException("unknown type");
            params.add(type);
        }

        Environment.Type ret;
        // handle return
        if(ast.getReturnTypeName().isPresent()){
            ret = Environment.getType(ast.getReturnTypeName().get());
            if(ret == null)
                throw new RuntimeException("unknown type");
        }
        else
            ret = Environment.Type.NIL;

        // create funciton
        Environment.Function func = new Environment.Function(
                                                    ast.getName(),
                                                    ast.getName(),
                                                    params,
                                                    ret,
                                                    args -> Environment.NIL);

        // define function in current scope
        scope.defineFunction(ast.getName(), ast.getName(), params, ret, args -> Environment.NIL);
        // set function
        ast.setFunction(func);

        // store global current return type for after try block
        Environment.Type prevRet = currentReturnType;

        try {
            scope = new Scope(scope);
            // define params and type
            for(int i = 0; i < ast.getParameters().size() - 1; i++) {
                scope.defineVariable(
                        ast.getParameters().get(i),
                        ast.getParameters().get(i),
                        Environment.getType(ast.getParameterTypeNames().get(i)),
                        false,
                        Environment.NIL);
            }
            // store method return type for visit(return)
            currentReturnType = ret;

            // visit method statements
            for (Ast.Statement stmnt : ast.getStatements())
                visit(stmnt);
        } finally {
            // restore scope and global return type
            scope = scope.getParent();
            currentReturnType = prevRet;
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        // check expression type and visit
        if(!(ast.getExpression() instanceof Ast.Expression.Function))
            throw new RuntimeException("Expression must be a function");
        else
            visit(ast.getExpression());
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {

        if(ast.getValue().isPresent())
            visit(ast.getValue().get());

        Environment.Type type;
        // check for explicit typename
        if(ast.getTypeName().isPresent())
            // get typename
            type = Environment.getType(ast.getTypeName().get());
        // if not there, infer type from value
        else if(ast.getValue().isPresent())
            type = ast.getValue().get().getType();
        else
            throw new RuntimeException("Type not found");

        // check for value and require assignable
        if (ast.getValue().isPresent())
            requireAssignable(type, ast.getValue().get().getType());

        // define variable in current scope
        scope.defineVariable(ast.getName(), ast.getName(), type, false, Environment.NIL);
        // set variable
        Environment.Variable var = scope.lookupVariable(ast.getName());
        ast.setVariable(var);

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        // check to make sure reciever is an Access
        if(!(ast.getReceiver() instanceof Ast.Expression.Access))
            throw new RuntimeException("Receiver must be an Access expression");

        // visit receiver
        Ast.Expression.Access receiver = (Ast.Expression.Access) ast.getReceiver();
        visit(receiver);

        // visit value
        Ast.Expression val = ast.getValue();
        visit(val);

        // check for constant
        if(receiver.getVariable().getConstant())
            throw new RuntimeException("can't change constant");

        // require types
        requireAssignable(receiver.getType(), val.getType());

        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        // visit condition
        visit(ast.getCondition());

        // check type
        if(ast.getCondition().getType() != Environment.Type.BOOLEAN)
            throw new RuntimeException("Condition must be a boolean");

        // visit then statements within it's scope
        if(!ast.getThenStatements().isEmpty()) {
            try {
                scope = new Scope(scope);
                for (Ast.Statement stmnt : ast.getThenStatements()) {
                    visit(stmnt);
                }
            } finally {
                scope = scope.getParent();
            }
        }
        // visit else statements within it's scope
        else if(!ast.getElseStatements().isEmpty()) {
            try {
                scope = new Scope(scope);
                for (Ast.Statement stmnt : ast.getElseStatements()) {
                    visit(stmnt);
                }
            }  finally {
                scope = scope.getParent();
            }
        }
        else
            throw new RuntimeException("thenStatements/elseStatements is empty");

        return null;
    }

    @Override
    public Void visit(Ast.Statement.For ast) {

        try {
            scope = new Scope(scope);

            Ast.Statement init = ast.getInitialization();
            if (init != null)
                visit(init);

            // get init access variable
            Ast.Expression.Access initAcc = (Ast.Expression.Access) (((Ast.Statement.Assignment) init).getReceiver());
            // get type
            Environment.Type initType = initAcc.getVariable().getType();
            // check type
            if(!isComparable(initType))
                throw new RuntimeException("identifier is not Comparable type");

            // visit condition and check type
            visit(ast.getCondition());
            requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());

            // check for increment and visit
            Ast.Statement incr = ast.getIncrement();
            if(incr != null)
                visit(incr);

            // get init access variable
            Ast.Expression.Access incrAcc = (Ast.Expression.Access) (((Ast.Statement.Assignment) init).getReceiver());
            // get type
            Environment.Type incrType = incrAcc.getVariable().getType();
            // make sure increment and initialization are the same type
            if(incrType != initType)
                throw new RuntimeException("Increment is not the same type as initialization");

            // check that statement is not empty and visit
            if(!(ast.getStatements().isEmpty())) {
                for (Ast.Statement stmt : ast.getStatements()) {
                    visit(stmt);
                }
            }
            else
                throw new RuntimeException("Statement is empty");


        } finally {
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {

        visit(ast.getCondition());
        requireAssignable(Environment.Type.BOOLEAN, ast.getCondition().getType());

        try {
            scope = new Scope(scope);
            for(Ast.Statement stmnt : ast.getStatements()) {
                visit(stmnt);
            }
        } finally {
            scope = scope.getParent();
        }

        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        // make sure value
        requireAssignable(ast.getValue().getType(), currentReturnType);
        visit(ast.getValue());

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {

        Object val = ast.getLiteral();

        if(val == null)
            ast.setType(Environment.Type.NIL);
        else if(val instanceof Boolean)
            ast.setType(Environment.Type.BOOLEAN);
        else if (val instanceof Character)
            ast.setType(Environment.Type.CHARACTER);
        else if (val instanceof String)
            ast.setType(Environment.Type.STRING);
        else if (val instanceof BigInteger) {
            BigInteger value = (BigInteger) val;

            try{
                value.intValueExact();
            }
            catch(ArithmeticException e) {
                throw new RuntimeException("Value exceeds 32 bits");
            }
            ast.setType(Environment.Type.INTEGER);
        }
        else if (val instanceof BigDecimal) {
            BigDecimal value = (BigDecimal) val;
            double dValue = value.doubleValue();
            if(Double.isInfinite(dValue))
                throw new RuntimeException("Value exceeds 64 bits");
            ast.setType(Environment.Type.DECIMAL);
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        // visit
        visit(ast.getExpression());

        // check for binary
        Ast.Expression expr = ast.getExpression();
        if(!(expr instanceof Ast.Expression.Binary))
            throw new RuntimeException("Expected binary expression in Group");

        // set type
        ast.setType(ast.getExpression().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {

        String op = ast.getOperator();
        // visit left and right
        visit(ast.getLeft());
        visit(ast.getRight());

        // get lhs and rhs types
        Environment.Type lhs = ast.getLeft().getType();
        Environment.Type rhs = ast.getRight().getType();

        switch (op) {
            case "AND":
            case "OR":{
                // check to make sure both sides are boolean
                if(lhs == Environment.Type.BOOLEAN && rhs == Environment.Type.BOOLEAN)
                    ast.setType(Environment.Type.BOOLEAN);
                else
                    throw new RuntimeException("lhs and rhs must both be boolean type");
                break;
            }
            case "<":
            case ">":
            case "<=":
            case ">=":
            case "==":
            case "!=": {
                // make sure both are the same type and comparable
                if(isComparable(lhs) && lhs == rhs )
                    ast.setType(Environment.Type.BOOLEAN);
                else
                    throw new RuntimeException("lhs and rhs must be comparable and the same type");
                break;
            }
            case "+": {
                if (lhs == Environment.Type.STRING || rhs == Environment.Type.STRING)
                    ast.setType(Environment.Type.STRING);
                else if(lhs == Environment.Type.INTEGER && lhs == rhs)
                    ast.setType(Environment.Type.INTEGER);
                else if(lhs == Environment.Type.DECIMAL && lhs == rhs)
                    ast.setType(Environment.Type.DECIMAL);
                else
                    throw new RuntimeException("lhs and rhs must be the same type");

                break;
            }
            case "-":
            case "*":
            case "/": {
                if(lhs == Environment.Type.INTEGER && lhs == rhs) {
                    ast.setType(Environment.Type.INTEGER);
                }
                else if(lhs == Environment.Type.DECIMAL && lhs == rhs) {
                    ast.setType(Environment.Type.DECIMAL);
                }
                else
                    throw new RuntimeException("lhs and rhs must be the same type");
                break;
            }
            default: throw new RuntimeException("Unknown operator: " + op);
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        //check for reciever
        if(ast.getReceiver().isPresent()) {

            // visit receiver
            visit(ast.getReceiver().get());

            // get type and field
            Environment.Type type = ast.getReceiver().get().getType();
            Environment.Variable field = type.getField(ast.getName());

            // set variable
            ast.setVariable(field);
        }
        else {
            // lookup variable
            Environment.Variable var = scope.lookupVariable(ast.getName());
            // set variable
            ast.setVariable(var);
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        // check for receiver
        if(ast.getReceiver().isPresent()) {
            // visit receiver
            visit(ast.getReceiver().get());

            // get type
            Environment.Type type = ast.getReceiver().get().getType();
            Environment.Function function = type.getFunction(ast.getName(), ast.getArguments().size());

            // set function
            ast.setFunction(function);

            // visit arguments and check type
            int i = 0;
            for(Ast.Expression arg : ast.getArguments()) {
                visit(arg);
                requireAssignable(function.getParameterTypes().get(i), arg.getType());
                i++;
            }

        }
        else {
            // lookup function
            Environment.Function function = scope.lookupFunction(ast.getName(), ast.getArguments().size());

            // set function
            ast.setFunction(function);

            // visit arguments and check type
            int i = 0;
            for(Ast.Expression arg : ast.getArguments()) {
                visit(arg);
                requireAssignable(function.getParameterTypes().get(i), arg.getType());
                i++;
            }

        }


        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        if(target == type)
            return;
        else if (target == Environment.Type.ANY)
            return;
        else if(target == Environment.Type.COMPARABLE) {
            if (type != Environment.Type.INTEGER
                    && type != Environment.Type.DECIMAL
                    && type != Environment.Type.STRING
                    && type != Environment.Type.CHARACTER)
                throw new RuntimeException("Type mismatch");
            else
                return;
        }
        throw new RuntimeException("Type mismatch");
    }

    public boolean isComparable(Environment.Type type) {
        if(type == Environment.Type.INTEGER || type == Environment.Type.DECIMAL || type == Environment.Type.STRING || type == Environment.Type.CHARACTER)
            return true;
        else
            return false;
    }
}
