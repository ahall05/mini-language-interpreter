package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            System.out.println(args.get(0).getValue());
            return Environment.NIL;
        });

        scope.defineFunction("logarithm", 1, args -> {
            if(!(args.get(0).getValue() instanceof BigInteger)) {
                throw new RuntimeException("Expected type BigDecimal, recieved" + args.get(0).getValue().getClass().getName() + ".");
            }

            BigDecimal bd1 = (BigDecimal) args.get(0).getValue();
            BigDecimal bd2 = requireType(BigDecimal.class, Environment.create(args.get(0).getValue()));

            BigDecimal result = BigDecimal.valueOf(Math.log(bd1.doubleValue()));

            return Environment.create(result);
        });

        scope.defineFunction("converter", 2, args -> {
            String number = new String();
            int i, n = 0;
            ArrayList<BigInteger> quotients =  new ArrayList<>();
            ArrayList<BigInteger> remainders = new ArrayList<>();

            // decimal store a base ten number
            BigInteger decimal = requireType(BigInteger.class, Environment.create(args.get(0).getValue()));
            BigInteger base = requireType(BigInteger.class, Environment.create(args.get(1).getValue()));

            quotients.add(decimal);

            do {
                quotients.add(quotients.get(n).divide(base));
                remainders.add(
                        quotients.get(n).subtract((quotients.get(n+1).multiply(base)))
                );
                n++;
            } while (quotients.get(n).compareTo(BigInteger.ZERO) > 0);

            for(i = 0; i < remainders.size(); i++) {
                number = remainders.get(i).toString() + number;
            }

            return Environment.create(number);
        });

    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {

        // define fields
        if(!ast.getFields().isEmpty())
            ast.getFields().forEach(this::visit);


        // define functions
        if(!ast.getMethods().isEmpty())
            ast.getMethods().forEach(this::visit);

        // call main and return result
        return scope.lookupFunction("main", 0).invoke(Arrays.asList());
    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {
        if(ast.getValue().isPresent())
            scope.defineVariable(ast.getName(), false, visit(ast.getValue().get()));
        else
            scope.defineVariable(ast.getName(), false, Environment.NIL);

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {

        // set the scope in which the function was defined
        Scope defScope = scope;

            scope.defineFunction(ast.getName(), ast.getParameters().size(),args ->
            {
                // store scope of the caller (for restoration purpose)
                Scope prev = scope;

                // set scope to be a new child of the defined scope
                scope = new Scope(defScope);

                try
                {
                    // define variables of parameters within scope
                    for (int i = 0; i < ast.getParameters().size(); i++)
                    {
                        scope.defineVariable(ast.getParameters().get(i), false, args.get(i));
                    }

                    // evaluate statements
                    for (Ast.Statement item : ast.getStatements())
                    {
                        visit(item);
                    }
                    return Environment.NIL;
                }
                catch (Return r)
                {
                    return r.value;
                }
                finally
                {
                    // restore scope of caller
                    scope = prev;
                }
            });

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Expression ast) {
        // evaluate expression
        visit(ast.getExpression());

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Declaration ast) {
        // check for initial value
        if(ast.getValue().isPresent())
            scope.defineVariable( ast.getName(),false, visit(ast.getValue().get()));
        else
            // default to NIL
            scope.defineVariable(ast.getName(), false, Environment.NIL);

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Assignment ast) {
        // make sure lhs is type .Access
        if(!(ast.getReceiver() instanceof Ast.Expression.Access))
            throw new RuntimeException("Must be type Ast.Expression.Access");

        // get rhs as PlcObject
        Environment.PlcObject rhs = visit(ast.getValue());

        // get Access object
        Ast.Expression.Access access = (Ast.Expression.Access) ast.getReceiver();

        // check for receiver
        if(access.getReceiver().isPresent())
        {
            // get receiver
            Environment.PlcObject receiver = visit(access.getReceiver().get());
            // within receiver, get the field corresponding to
            // access.getName(), then set its value to rhs
            receiver.getField(access.getName()).setValue(rhs);
        }
        // else lookup the existing variable
        else
        {
            // set value of existing variable
            scope.lookupVariable(access.getName()).setValue(rhs);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.If ast) {
        // get condition
        Environment.PlcObject condition = visit(ast.getCondition());

        // evaluate statements
        if(requireType(Boolean.class, condition))
            ast.getThenStatements().forEach(this::visit);
        else
            ast.getElseStatements().forEach(this::visit);

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.For ast) {
        // similar to while loop, but with an initialization and increment step

        // initialize once
        visit(ast.getInitialization());

        while(requireType(Boolean.class, visit(ast.getCondition()))) {
            try {
                scope = new Scope(scope);
                ast.getStatements().forEach(this::visit);
            } finally {
                scope = scope.getParent();
            }
            // update increment
            visit(ast.getIncrement());
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.While ast) {

        while(requireType(Boolean.class, visit(ast.getCondition()))) {
            try
            {
                scope = new Scope(scope);
                ast.getStatements().forEach(this::visit);
            }
            finally
            {
                scope = scope.getParent();
            }
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Statement.Return ast) {

        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Literal ast) {
        // check for null and return literal
        if(ast.getLiteral() == null)
            return Environment.NIL;
        else
            return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Group ast) {
        return visit(ast.getExpression());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Binary ast) {
        Environment.PlcObject left = visit(ast.getLeft());
        // right is evaluated when necessary inside each case to handle "undefined" test case
        String op = ast.getOperator();

        switch (op)
        {
            case "AND":
            {
                Boolean lBool = requireType(Boolean.class, left);
                // if left is false, don't look at right
                if (!lBool)
                    return Environment.create(Boolean.FALSE);

                // else return rBool
                Environment.PlcObject right = visit(ast.getRight());
                Boolean rBool = requireType(Boolean.class, right);
                return Environment.create(rBool);
            }
            case "OR":
            {
                Boolean lBool = requireType(Boolean.class, left);
                // if left is true, return true
                if (lBool)
                    return Environment.create(Boolean.TRUE);
                // else return right
                else
                {
                    Environment.PlcObject right = visit(ast.getRight());
                    Boolean rBool = requireType(Boolean.class, right);
                    return Environment.create(rBool);
                }
            }
            case "<":
            {
                Environment.PlcObject right = visit(ast.getRight());
                // check for comparable
                if(!(left.getValue() instanceof Comparable))
                    throw new RuntimeException("LHS not comparable");
                if(!(right.getValue() instanceof Comparable))
                    throw new RuntimeException("RHS not comparable");

                // compare and return
                Comparable l = (Comparable) left.getValue();
                return Environment.create(l.compareTo(right.getValue()) < 0);
            }
            case "<=":
            {
                Environment.PlcObject right = visit(ast.getRight());
                // check for comparable
                if(!(left.getValue() instanceof Comparable))
                    throw new RuntimeException("LHS not comparable");
                if(!(right.getValue() instanceof Comparable))
                    throw new RuntimeException("RHS not comparable");

                // compare and return
                Comparable l = (Comparable) left.getValue();
                return Environment.create(l.compareTo(right.getValue()) <= 0);
            }
            case ">":
            {
                Environment.PlcObject right = visit(ast.getRight());
                // check for comparable
                if(!(left.getValue() instanceof Comparable))
                    throw new RuntimeException("LHS not comparable");
                if(!(right.getValue() instanceof Comparable))
                    throw new RuntimeException("RHS not comparable");

                // compare and return
                Comparable l = (Comparable) left.getValue();
                return Environment.create(l.compareTo(right.getValue()) > 0);
            }
            case ">=":
            {
                Environment.PlcObject right = visit(ast.getRight());
                // check for comparable
                if(!(left.getValue() instanceof Comparable))
                    throw new RuntimeException("LHS not comparable");
                if(!(right.getValue() instanceof Comparable))
                    throw new RuntimeException("RHS not comparable");

                // compare and return
                Comparable l = (Comparable) left.getValue();
                return Environment.create(l.compareTo(right.getValue()) >= 0);
            }
            case "==":
            {
                Environment.PlcObject right = visit(ast.getRight());

                Object lValue = left.getValue();
                Object rValue = right.getValue();
                return Environment.create(java.util.Objects.equals(lValue, rValue));
            }
            case "!=":
            {
                Environment.PlcObject right = visit(ast.getRight());

                Object lValue = left.getValue();
                Object rValue = right.getValue();
                // negate .equals()
                return Environment.create(!java.util.Objects.equals(lValue, rValue));
            }
            case "+":
            {
                Environment.PlcObject right = visit(ast.getRight());
                Object l = left.getValue();
                Object r = right.getValue();

                // check for string concatenation
                if(l instanceof String || r instanceof String)
                    return Environment.create(l.toString() + r.toString());
                //else check for integer/decimal
                else if (l instanceof BigInteger && r instanceof BigInteger)
                    return Environment.create(((BigInteger) l).add((BigInteger) r));
                else if (l instanceof BigDecimal && r instanceof BigDecimal)
                    return Environment.create(((BigDecimal) l).add((BigDecimal) r));
                else
                    throw new RuntimeException("Operands must be String, BigInteger, or BigDecimal");
            }
            case "-":
            {
                Environment.PlcObject right = visit(ast.getRight());
                Object l = left.getValue();
                Object r = right.getValue();

                // check for integer
                if(l instanceof BigInteger)
                {
                    // make sure types match
                    if(r instanceof BigInteger)
                        return Environment.create(((BigInteger) l).subtract((BigInteger) r));
                    else
                        throw new RuntimeException("RHS must also be BigInteger");
                }
                // check for decimal
                else if(l instanceof BigDecimal)
                {
                    // make sure types match
                    if(r instanceof BigDecimal)
                        return Environment.create(((BigDecimal) l).subtract((BigDecimal) r));
                    else
                        throw new RuntimeException("RHS must also be BigDecimal");
                }
            }
            case "*":
            {
                Environment.PlcObject right = visit(ast.getRight());

                Object l = left.getValue();
                Object r = right.getValue();

                //check for BigInteger
                if(l instanceof BigInteger)
                {
                    // enforce type match
                    if(r instanceof BigInteger)
                        return Environment.create(((BigInteger) l).multiply((BigInteger) r));
                    else
                        throw new RuntimeException("RHS must be BigInteger");
                }
                // check for BigDecimal
                if(l instanceof BigDecimal)
                {
                    // enforce type match
                    if(r instanceof BigDecimal)
                        return Environment.create(((BigDecimal) l).multiply((BigDecimal) r));
                    else
                        throw new RuntimeException("RHS must be BigDecimal");
                }
            }
            case "/":
            {
                Environment.PlcObject right = visit(ast.getRight());
                Object l = left.getValue();
                Object r = right.getValue();

                if(l instanceof BigInteger && r instanceof BigInteger)
                {
                    // check for division by zero
                        if(!(((BigInteger) r).equals(BigInteger.ZERO)))
                            return Environment.create(((BigInteger) l).divide((BigInteger) r));
                        else
                            throw new RuntimeException("Cannot divide by zero");
                }
                if(l instanceof BigDecimal && r instanceof BigDecimal)
                {
                    // check for division by zero
                    if (!(r.equals(BigDecimal.ZERO)))
                        return Environment.create(((BigDecimal) l).divide((BigDecimal) r, RoundingMode.HALF_EVEN));
                    else
                        throw new RuntimeException("Cannot divide by zero");
                }
                else
                    throw new RuntimeException("Operands don't match, must be BigInteger or BigDecimal");
            }
            default:
                throw new RuntimeException("Unknown operator: " + op);
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Access ast) {


        if(ast.getReceiver().isPresent())
        {
            // get receiver
            Environment.PlcObject receiver = visit(ast.getReceiver().get());
            // return value of appropriate field
            return receiver.getField(ast.getName()).getValue();
        }
        else
        {
            // get variable and return value of variable in scope
            Environment.Variable var = scope.lookupVariable(ast.getName());
            return var.getValue();
        }
    }

    @Override
    public Environment.PlcObject visit(Ast.Expression.Function ast) {
        // get arguments
        List<Environment.PlcObject> args = new ArrayList<>();
        for (Ast.Expression item : ast.getArguments())
            args.add(visit(item));


        if(ast.getReceiver().isPresent())
        {
            // get receiver
            Environment.PlcObject receiver = visit(ast.getReceiver().get());
            // return callmethod with args
            return receiver.callMethod(ast.getName(), args);
        }
        else
        {
            // look up function
            Environment.Function func = scope.lookupFunction(ast.getName(), args.size());
            // invoke and return result
            return func.invoke(args);
        }
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
