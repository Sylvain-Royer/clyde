//
// $Id$
//
// Clyde library - tools for developing networked games
// Copyright (C) 2005-2010 Three Rings Design, Inc.
//
// Redistribution and use in source and binary forms, with or without modification, are permitted
// provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of
//    conditions and the following disclaimer in the documentation and/or other materials provided
//    with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.threerings.expr;

import java.io.StringReader;

import com.threerings.editor.Editable;
import com.threerings.editor.EditorTypes;
import com.threerings.export.Exportable;
import com.threerings.math.FloatMath;
import com.threerings.util.DeepObject;
import com.threerings.util.DeepOmit;
import com.threerings.util.ExpressionParser;

import com.threerings.expr.util.ScopeUtil;

/**
 * A float-valued expression.
 */
@EditorTypes({
    FloatExpression.Parsed.class, FloatExpression.Constant.class,
    FloatExpression.Reference.class, FloatExpression.Clock.class,
    FloatExpression.Negate.class, FloatExpression.Add.class,
    FloatExpression.Subtract.class, FloatExpression.Multiply.class,
    FloatExpression.Divide.class, FloatExpression.Remainder.class,
    FloatExpression.Pow.class, FloatExpression.Sin.class,
    FloatExpression.Cos.class, FloatExpression.Tan.class })
public abstract class FloatExpression extends DeepObject
    implements Exportable
{
    /**
     * An expression entered as a string to be parsed.
     */
    public static class Parsed extends FloatExpression
    {
        /** The expression to parse. */
        @Editable
        public String expression = "0.0";

        @Override // documentation inherited
        public Evaluator createEvaluator (Scope scope)
        {
            if (_expr == null) {
                try {
                    _expr = parseExpression(expression);
                } catch (Exception e) {
                    // don't worry about it; it's probably being entered
                }
                if (_expr == null) {
                    _expr = new Constant(0f);
                }
            }
            return _expr.createEvaluator(scope);
        }

        @Override // documentation inherited
        public void invalidate ()
        {
            _expr = null;
        }

        /** The cached, parsed expression. */
        @DeepOmit
        protected transient FloatExpression _expr;
    }

    /**
     * A constant expression.
     */
    public static class Constant extends FloatExpression
    {
        /** The value of the constant. */
        @Editable(step=0.01)
        public float value;

        /**
         * Creates a new constant expression with the specified value.
         */
        public Constant (float value)
        {
            this.value = value;
        }

        /**
         * Creates a new constant expression with a value of zero.
         */
        public Constant ()
        {
        }

        @Override // documentation inherited
        public Evaluator createEvaluator (Scope scope)
        {
            return new Evaluator() {
                public float evaluate () {
                    return value;
                }
            };
        }
    }

    /**
     * A reference expression.
     */
    public static class Reference extends FloatExpression
    {
        /** The name of the variable. */
        @Editable(hgroup="n")
        public String name = "";

        /** The default value of the variable. */
        @Editable(step=0.01, hgroup="n")
        public float defvalue;

        @Override // documentation inherited
        public Evaluator createEvaluator (Scope scope)
        {
            // first look for a mutable reference, then for a variable
            final MutableFloat reference = ScopeUtil.resolve(
                scope, name, (MutableFloat)null);
            if (reference != null) {
                return new Evaluator() {
                    public float evaluate () {
                        return reference.value;
                    }
                };
            }
            final Variable variable = ScopeUtil.resolve(
                scope, name, Variable.newInstance(defvalue));
            return new Evaluator() {
                public float evaluate () {
                    return variable.getFloat();
                }
            };
        }
    }

    /**
     * A clock-based expression.
     */
    public static class Clock extends FloatExpression
    {
        /** The scope of the epoch reference. */
        @Editable
        public String scope = "";

        @Override // documentation inherited
        public Evaluator createEvaluator (Scope scope)
        {
            String name = this.scope.trim();
            name = (name.length() > 0) ? (name + ":" + Scope.EPOCH) : Scope.EPOCH;
            MutableLong defvalue = new MutableLong(System.currentTimeMillis());
            final MutableLong epoch = ScopeUtil.resolve(scope, name, defvalue);
            final MutableLong now = ScopeUtil.resolve(scope, Scope.NOW, defvalue);
            return new Evaluator() {
                public float evaluate () {
                    return (now.value - epoch.value) / 1000f;
                }
            };
        }
    }

    /**
     * The superclass of the unary operations.
     */
    public static abstract class UnaryOperation extends FloatExpression
    {
        /** The operand expression. */
        @Editable
        public FloatExpression operand = new Constant();

        @Override // documentation inherited
        public Evaluator createEvaluator (Scope scope)
        {
            return createEvaluator(operand.createEvaluator(scope));
        }

        @Override // documentation inherited
        public void invalidate ()
        {
            operand.invalidate();
        }

        /**
         * Creates the evaluator for this expression, given the evaluator for its operand.
         */
        protected abstract Evaluator createEvaluator (Evaluator eval);
    }

    /**
     * Negates its operand.
     */
    public static class Negate extends UnaryOperation
    {
        @Override // documentation inherited
        protected Evaluator createEvaluator (final Evaluator eval)
        {
            return new Evaluator() {
                public float evaluate () {
                    return -eval.evaluate();
                }
            };
        }
    }

    /**
     * Computes the sine of its operand.
     */
    public static class Sin extends UnaryOperation
    {
        @Override // documentation inherited
        protected Evaluator createEvaluator (final Evaluator eval)
        {
            return new Evaluator() {
                public float evaluate () {
                    return FloatMath.sin(eval.evaluate());
                }
            };
        }
    }

    /**
     * Computes the cosine of its operand.
     */
    public static class Cos extends UnaryOperation
    {
        @Override // documentation inherited
        protected Evaluator createEvaluator (final Evaluator eval)
        {
            return new Evaluator() {
                public float evaluate () {
                    return FloatMath.cos(eval.evaluate());
                }
            };
        }
    }

    /**
     * Computes the tangent of its operand.
     */
    public static class Tan extends UnaryOperation
    {
        @Override // documentation inherited
        protected Evaluator createEvaluator (final Evaluator eval)
        {
            return new Evaluator() {
                public float evaluate () {
                    return FloatMath.tan(eval.evaluate());
                }
            };
        }
    }

    /**
     * The superclass of the binary operations.
     */
    public static abstract class BinaryOperation extends FloatExpression
    {
        /** The first operand expression. */
        @Editable
        public FloatExpression firstOperand = new Constant();

        /** The second operand expression. */
        @Editable
        public FloatExpression secondOperand = new Constant();

        @Override // documentation inherited
        public Evaluator createEvaluator (Scope scope)
        {
            return createEvaluator(
                firstOperand.createEvaluator(scope), secondOperand.createEvaluator(scope));
        }

        @Override // documentation inherited
        public void invalidate ()
        {
            firstOperand.invalidate();
            secondOperand.invalidate();
        }

        /**
         * Creates the evaluator for this expression, given the evaluators for its operands.
         */
        protected abstract Evaluator createEvaluator (Evaluator eval1, Evaluator eval2);
    }

    /**
     * Adds its operands.
     */
    public static class Add extends BinaryOperation
    {
        @Override // documentation inherited
        protected Evaluator createEvaluator (final Evaluator eval1, final Evaluator eval2)
        {
            return new Evaluator() {
                public float evaluate () {
                    return eval1.evaluate() + eval2.evaluate();
                }
            };
        }
    }

    /**
     * Subtracts the second operand from the first.
     */
    public static class Subtract extends BinaryOperation
    {
        @Override // documentation inherited
        protected Evaluator createEvaluator (final Evaluator eval1, final Evaluator eval2)
        {
            return new Evaluator() {
                public float evaluate () {
                    return eval1.evaluate() - eval2.evaluate();
                }
            };
        }
    }

    /**
     * Multiplies its operands.
     */
    public static class Multiply extends BinaryOperation
    {
        @Override // documentation inherited
        protected Evaluator createEvaluator (final Evaluator eval1, final Evaluator eval2)
        {
            return new Evaluator() {
                public float evaluate () {
                    return eval1.evaluate() * eval2.evaluate();
                }
            };
        }
    }

    /**
     * Divides the first operand by the second.
     */
    public static class Divide extends BinaryOperation
    {
        @Override // documentation inherited
        protected Evaluator createEvaluator (final Evaluator eval1, final Evaluator eval2)
        {
            return new Evaluator() {
                public float evaluate () {
                    return eval1.evaluate() / eval2.evaluate();
                }
            };
        }
    }

    /**
     * Computes the floating point remainder when the first operand is divided by the second.
     */
    public static class Remainder extends BinaryOperation
    {
        @Override // documentation inherited
        protected Evaluator createEvaluator (final Evaluator eval1, final Evaluator eval2)
        {
            return new Evaluator() {
                public float evaluate () {
                    return eval1.evaluate() % eval2.evaluate();
                }
            };
        }
    }

    /**
     * Raises the first operand to the power of the second.
     */
    public static class Pow extends BinaryOperation
    {
        @Override // documentation inherited
        protected Evaluator createEvaluator (final Evaluator eval1, final Evaluator eval2)
        {
            return new Evaluator() {
                public float evaluate () {
                    return FloatMath.pow(eval1.evaluate(), eval2.evaluate());
                }
            };
        }
    }

    /**
     * Performs the actual evaluation of the expression.
     */
    public static abstract class Evaluator
    {
        /**
         * Evaluates and returns the current value of the expression.
         */
        public abstract float evaluate ();
    }

    /**
     * Creates an expression evaluator for the supplied context.
     */
    public abstract Evaluator createEvaluator (Scope scope);

    /**
     * Invalidates any cached data.
     */
    public void invalidate ()
    {
        // nothing by default
    }

    /**
     * Parses the supplied string expression.
     */
    protected static FloatExpression parseExpression (String expression)
        throws Exception
    {
        return (FloatExpression)new ExpressionParser<Object>(new StringReader(expression)) {
            @Override protected Object handleNumber (double value) {
                return new Constant((float)value);
            }
            @Override protected Object handleString (String value) {
                return value;
            }
            @Override protected Object handleOperator (String operator, int arity)
                    throws Exception {
                if (arity == 1) {
                    if (operator.equals("+")) {
                        return (FloatExpression)_output.pop();

                    } else if (operator.equals("-")) {
                        Negate negate = new Negate();
                        negate.operand = (FloatExpression)_output.pop();
                        return negate;

                    } else {
                        return super.handleOperator(operator, arity);
                    }
                } else { // arity == 2
                    BinaryOperation result;
                    if (operator.equals("+")) {
                        result = new Add();
                    } else if (operator.equals("-")) {
                        result = new Subtract();
                    } else if (operator.equals("*")) {
                        result = new Multiply();
                    } else if (operator.equals("/")) {
                        result = new Divide();
                    } else if (operator.equals("%")) {
                        result = new Remainder();
                    } else {
                        return super.handleOperator(operator, arity);
                    }
                    result.secondOperand = (FloatExpression)_output.pop();
                    result.firstOperand = (FloatExpression)_output.pop();
                    return result;
                }
            }
            @Override protected Object handleFunctionCall (String function, int arity)
                    throws Exception {
                UnaryOperation result;
                if (function.equals("clock")) {
                    assertArity("clock", arity, 0, 1);
                    Clock clock = new Clock();
                    clock.scope = (arity == 1) ? (String)_output.pop() : "";
                    return clock;
                } else if (function.equals("pow")) {
                    assertArity("pow", arity, 2, 2);
                    Pow pow = new Pow();
                    pow.secondOperand = (FloatExpression)_output.pop();
                    pow.firstOperand = (FloatExpression)_output.pop();
                    return pow;
                } else if (function.equals("sin")) {
                    result = new Sin();
                } else if (function.equals("cos")) {
                    result = new Cos();
                } else if (function.equals("tan")) {
                    result = new Tan();
                } else {
                    return super.handleFunctionCall(function, arity);
                }
                assertArity(function, arity, 1, 1);
                result.operand = (FloatExpression)_output.pop();
                return result;
            }
            @Override protected Object handleIdentifier (String name) {
                Reference ref = new Reference();
                ref.name = name;
                return ref;
            }
            protected void assertArity (String function, int arity, int min, int max)
                throws Exception {
                if (arity < min || arity > max) {
                    throw new Exception("Wrong number of arguments for " + function);
                }
            }
        }.parse();
    }
}
