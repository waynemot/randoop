package randoop.operation;

import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import randoop.ExceptionalExecution;
import randoop.ExecutionOutcome;
import randoop.NormalExecution;
import randoop.reflection.TypedOperationManager;
import randoop.reflection.ReflectionPredicate;
import randoop.sequence.Statement;
import randoop.sequence.Variable;
import randoop.types.TypeTuple;
import randoop.types.GeneralType;
import randoop.types.RandoopTypeException;
import randoop.types.TypeNames;
import randoop.util.ConstructorReflectionCode;
import randoop.util.ReflectionExecutor;
import randoop.util.Util;

/**
 * ConstructorCall is an {@link Operation} that represents a call to a
 * constructor, and holds a reference to a reflective
 * {@link java.lang.reflect.Constructor} object.
 *
 * As an {@link Operation}, a call to constructor <i>c</i> with <i>n</i>
 * arguments is represented as <i>c</i> : [<i>t1,...,tn</i>] &rarr; <i>c</i>,
 * where the output type <i>c</i> is also the name of the class.
 */
public final class ConstructorCall extends CallableOperation {

  /**
   * ID for parsing purposes.
   *
   * @see OperationParser#getId(TypedOperation)
   */
  public static final String ID = "cons";

  private final Constructor<?> constructor;

  // Cached values (for improved performance). Their values
  // are computed upon the first invocation of the respective
  // getter method.
  private int hashCodeCached = 0;
  private boolean hashCodeComputed = false;

  /**
   * Creates object corresponding to the given reflection constructor.
   *
   * @param constructor
   *          reflective object for a constructor.
   */
  public ConstructorCall(Constructor<?> constructor) {
    if (constructor == null) throw new IllegalArgumentException("constructor should not be null.");
    this.constructor = constructor;
    this.constructor.setAccessible(true);
  }

  /**
   * Return the reflective constructor corresponding to this ConstructorCall.
   *
   * @return {@link Constructor} object called by this constructor call.
   */
  public Constructor<?> getConstructor() {
    return this.constructor;
  }

  /**
   * Returns concise string representation of this ConstructorCall.
   */
  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(constructor.getName());
    b.append("(");
    Class<?>[] types = constructor.getParameterTypes();
    if (types.length > 0) {
      b.append(types[0].getName());
      for (int i = 1; i < types.length; i++) {
        b.append(", ").append(types[i].getName());
      }
    }
    b.append(")");
    return b.toString();
  }

  @Override
  public String getName() {
    return "<init>";
  }

  /**
   * Adds code for a constructor call to the given {@link StringBuilder}.
   *
   * @param inputVars
   *          a list of variables representing the actual arguments for the
   *          constructor call.
   * @param b
   *          the StringBuilder to which the output is appended.
   * @see TypedOperation#appendCode(List, StringBuilder)
   */
  @Override
  public void appendCode(GeneralType declaringType, TypeTuple inputTypes, GeneralType outputType, List<Variable> inputVars, StringBuilder b) {

    Class<?> declaringClass = constructor.getDeclaringClass();
    boolean isNonStaticMember =
        (!Modifier.isStatic(declaringClass.getModifiers()) && declaringClass.isMemberClass());
    assert Util.implies(isNonStaticMember, inputVars.size() > 0);

    // Note on isNonStaticMember: if a class is a non-static member class, the
    // runtime signature of the constructor will have an additional argument
    // (as the first argument) corresponding to the owning object. When printing
    // it out as source code, we need to treat it as a special case: instead
    // of printing "new Foo(x,y.z)" we have to print "x.new Foo(y,z)".

    // TODO the last replace is ugly. There should be a method that does it.
    String declaringClassStr = TypeNames.getCompilableName(declaringClass);

    b.append(isNonStaticMember ? inputVars.get(0) + "." : "")
            .append("new ")
            .append(isNonStaticMember ? declaringClass.getSimpleName() : declaringClassStr)
            .append("(");
    for (int i = (isNonStaticMember ? 1 : 0); i < inputVars.size(); i++) {
      if (i > (isNonStaticMember ? 1 : 0)) b.append(", ");

      // We cast whenever the variable and input types are not identical.
      if (!inputVars.get(i).getType().equals(inputTypes.get(i)))
        b.append("(").append(inputTypes.get(i).getName()).append(")");

      String param = inputVars.get(i).getName();

      // In the short output format, statements that assign to a primitive
      // or string literal, like "int x = 3" are not added to a sequence;
      // instead, the value (e.g. "3") is inserted directly added as
      // arguments to method calls.
      Statement statementCreatingVar = inputVars.get(i).getDeclaringStatement();

      String shortForm = statementCreatingVar.getShortForm();
      if (shortForm != null) {
        param = shortForm;
      }

      b.append(param);
    }
    b.append(")");
  }

  /**
   * Tests whether the parameter is a call to the same constructor.
   *
   * @param o
   *          an object
   * @return true if o is a ConstructorCall referring to same constructor
   *         object; false otherwise.
   */
  @Override
  public boolean equals(Object o) {
    if (o instanceof ConstructorCall) {
      if (this == o) return true;

      ConstructorCall other = (ConstructorCall) o;
      return this.constructor.equals(other.constructor);
    }
    return false;
  }

  /**
   * hashCode returns the hashCode for the constructor called by this object.
   */
  @Override
  public int hashCode() {
    if (!hashCodeComputed) {
      hashCodeComputed = true;
      hashCodeCached = this.constructor.hashCode();
    }
    return hashCodeCached;
  }

  /**
   * {@inheritDoc} Performs call to the constructor given the objects as actual
   * parameters, and the output stream for any output.
   *
   * @param statementInput
   *          is an array of values corresponding to signature of the
   *          constructor.
   * @param out
   *          is a stream for any output.
   * @see TypedOperation#execute(Object[], PrintStream)
   */
  @Override
  public ExecutionOutcome execute(Object[] statementInput, PrintStream out) {

    ConstructorReflectionCode code =
        new ConstructorReflectionCode(this.constructor, statementInput);

    Throwable thrown = ReflectionExecutor.executeReflectionCode(code, out);

    if (thrown == null) {
      return new NormalExecution(code.getReturnVariable(), 0);
    } else {
      return new ExceptionalExecution(thrown, 0);
    }
  }

  /**
   * {@inheritDoc}
   * Generates a string representation of the constructor signature.
   *
   * Examples:
   *
   * <pre>
   * <code>
   *  java.util.ArrayList.&lt;init&gt;()
   *  java.util.ArrayList.&lt;init&gt;(java.util.Collection)
   * </code>
   * </pre>
   *
   * @see #parse(String, TypedOperationManager)
   *
   * @return signature string for constructor.
   */
  @Override
  public String toParseableString(GeneralType declaringType, TypeTuple inputTypes, GeneralType outputType) {
    StringBuilder sb = new StringBuilder();
    sb.append(constructor.getName()).append(".<init>(");
    Class<?>[] params = constructor.getParameterTypes();
    TypeArguments.getTypeArgumentString(sb, params);
    sb.append(")");
    return sb.toString();
  }

  /**
   * Parse a constructor call in a string with the format generated by
   * {@link ConstructorCall#toParseableString(GeneralType, TypeTuple, GeneralType)} and
   * returns the corresponding {@link ConstructorCall} object.
   *
   * @see OperationParser#parse(String, randoop.reflection.TypedOperationManager)
   *
   * @param signature
   *          a string descriptor of a constructor call.
   * @param manager  the {@link TypedOperationManager} for collecting operations
   * @throws OperationParseException
   *           if no constructor found for signature.
   */
  public static void parse(String signature, TypedOperationManager manager) throws OperationParseException {
    if (signature == null) {
      throw new IllegalArgumentException("signature may not be null");
    }

    int openParPos = signature.indexOf('(');
    int closeParPos = signature.indexOf(')');

    String prefix = signature.substring(0, openParPos);
    int lastDotPos = prefix.lastIndexOf('.');

    assert lastDotPos >= 0;
    String classname = prefix.substring(0, lastDotPos);
    String opname = prefix.substring(lastDotPos + 1);
    assert opname.equals("<init>") : "expected init, saw " + opname;
    String arguments = signature.substring(openParPos + 1, closeParPos);

    String constructorString = classname + "." + opname + arguments;
    GeneralType classType;
    try {
      classType = GeneralType.forName(classname);
    } catch (ClassNotFoundException e) {
      String msg = "Class for constructor " + constructorString + " not found: " + e;
      throw new OperationParseException(msg);
    } catch (RandoopTypeException e) {
      String msg = "Type error for constructor " + constructorString + ": " + e.getMessage();
      throw new OperationParseException(msg);
    }

    Class<?>[] typeArguments = TypeArguments.getTypeArgumentsForString(arguments);
    Constructor<?> con;
    try {
      con = classType.getRuntimeClass().getDeclaredConstructor(typeArguments);
    } catch (NoSuchMethodException e) {
      String msg = "Constructor " + constructorString + " not found: " + e;
      throw new OperationParseException(msg);
    }

    ConstructorCall op = new ConstructorCall(con);
    List<GeneralType> paramTypes = new ArrayList<>();
    for (Class<?> c : typeArguments) {
      try {
        paramTypes.add(manager.getClassType(c));
      } catch (RandoopTypeException e) {
        String msg = "Type error when parsing constructor " + constructorString + ": " + e.getMessage();
        throw new OperationParseException(msg);
      }
    }

    manager.createTypedOperation(op, classType, new TypeTuple(paramTypes), classType);
  }

  /**
   * {@inheritDoc}
   *
   * @return true, because this is a {@link ConstructorCall}.
   */
  @Override
  public boolean isConstructorCall() {
    return true;
  }

  /**
   * {@inheritDoc} Determines whether enclosed {@link Constructor} satisfies the
   * given predicate.
   *
   * @return true only if the constructor in this object satisfies the
   *         {@link ReflectionPredicate#test(Constructor)} implemented by
   *         predicate.
   */
  @Override
  public boolean satisfies(ReflectionPredicate predicate) {
    return predicate.test(constructor);
  }
}
