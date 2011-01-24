/*
 * Copyright 2009-11 Michael Bedward
 * 
 * This file is part of jai-tools.
 *
 * jai-tools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 *
 * jai-tools is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public 
 * License along with jai-tools.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package jaitools.jiffle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.antlr.runtime.tree.BufferedTreeNodeStream;
import org.antlr.runtime.tree.CommonTree;
import org.antlr.runtime.tree.CommonTreeNodeStream;

import org.codehaus.janino.SimpleCompiler;

import jaitools.jiffle.parser.ErrorCode;
import jaitools.CollectionFactory;
import jaitools.jiffle.parser.CompilerExitException;
import jaitools.jiffle.parser.ConvertTernaryExpr;
import jaitools.jiffle.parser.DeferredErrorReporter;
import jaitools.jiffle.parser.DirectRuntimeSourceCreator;
import jaitools.jiffle.parser.FunctionValidator;
import jaitools.jiffle.parser.IndirectRuntimeSourceCreator;
import jaitools.jiffle.parser.JiffleLexer;
import jaitools.jiffle.parser.JiffleParser;
import jaitools.jiffle.parser.ParsingErrorReporter;
import jaitools.jiffle.parser.RuntimeSourceCreator;
import jaitools.jiffle.parser.VarClassifier;
import jaitools.jiffle.parser.VarTransformer;
import jaitools.jiffle.runtime.JiffleDirectRuntime;
import jaitools.jiffle.runtime.JiffleIndirectRuntime;
import jaitools.jiffle.runtime.JiffleRuntime;

/**
 * Compiles scripts into runtime objects.
 * <p>
 * DOCUMENT ME PROPERLY !
 * 
 * @author Michael Bedward
 * @since 1.0
 * @source $URL$
 * @version $Id$
 */
public class Jiffle {
    
    /** Constants for runtime classes. */
    public static enum EvaluationModel {
        /** The runtime class implements {@link JiffleDirectRuntime} */
        DIRECT(JiffleDirectRuntime.class),
        
        /** The runtime class implements {@link JiffleIndirectRuntime} */
        INDIRECT(JiffleIndirectRuntime.class);
        
        private Class<? extends JiffleRuntime> runtimeClass;
        
        private EvaluationModel(Class<? extends JiffleRuntime> clazz) {
            this.runtimeClass = clazz;
        }

        public Class<? extends JiffleRuntime> getRuntimeClass() {
            return runtimeClass;
        }
        
        public static EvaluationModel get(Class<? extends JiffleRuntime> clazz) {
            for (EvaluationModel t : EvaluationModel.values()) {
                if (t.runtimeClass.isAssignableFrom(clazz)) {
                    return t;
                }
            }
            
            return null;
        }
    }
    
    private static int refCount = 0;
    
    private static final String PROPERTIES_FILE = "META-INF/jiffle.properties";

    private static final Properties properties = new Properties();
    
    private static final String NAME_KEY = "root.name";
    private static final String RUNTIME_PACKAGE_KEY = "runtime.package";
    
    private static final String DIRECT_CLASS_KEY = "direct.class";
    private static final String DIRECT_BASE_CLASS_KEY = "direct.base.class";
    
    private static final String INDIRECT_CLASS_KEY = "indirect.class";
    private static final String INDIRECT_BASE_CLASS_KEY = "indirect.base.class";
    
    private static final String IMPORTS_KEY = "runtime.imports";
    
    /** Delimiter used to separate multiple import entries */
    private static final String RUNTIME_IMPORTS_DELIM = ";";
    
    private static final Class<? extends JiffleRuntime> DEFAULT_DIRECT_BASE_CLASS;
    private static final Class<? extends JiffleRuntime> DEFAULT_INDIRECT_BASE_CLASS;
    
    static {
        InputStream in = null;
        try {
            in = Jiffle.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE);
            properties.load(in);
            
            String className = properties.getProperty(RUNTIME_PACKAGE_KEY) + "." +
                    properties.getProperty(DIRECT_BASE_CLASS_KEY);
            
            DEFAULT_DIRECT_BASE_CLASS = (Class<? extends JiffleRuntime>) Class.forName(className);
            
            className = properties.getProperty(RUNTIME_PACKAGE_KEY) + "." +
                    properties.getProperty(INDIRECT_BASE_CLASS_KEY);
            
            DEFAULT_INDIRECT_BASE_CLASS = (Class<? extends JiffleRuntime>) Class.forName(className);
            
        } catch (Exception ex) {
            throw new IllegalArgumentException("Internal compiler error", ex);
            
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ex) {
                // ignore
            }
        }

    }

    /**
     * Used to specify the roles of images referenced in
     * a Jiffle script. An image may be either read-only
     * ({@link Jiffle.ImageRole#SOURCE}) or write-only
     * ({@link Jiffle.ImageRole#DEST}) but not both.
     */
    public static enum ImageRole {
        /** Indicates an image is used for input (read-only) */
        SOURCE,
        
        /** Indicates an image is used for output (write-only) */
        DEST;
    }

    /** A name: either a default or one set by the client */
    private String name;

    private String theScript;
    private CommonTree primaryAST;
    private CommonTree finalAST;
    private CommonTokenStream tokens;
    private ParsingErrorReporter errorReporter;
    
    private Map<String, ImageRole> imageParams;
    private Map<String, ErrorCode> errors;
    
    
    /**
     * Creates a new instance.
     */
    public Jiffle() {
        init();
    }
    
    /**
     * Creates a new instance by compiling the provided script. Using this
     * constructor is equivalent to:
     * <pre><code>
     * Jiffle jiffle = new Jiffle();
     * jiffle.setScript(script);
     * jiffle.setImageParams(params);
     * jiffle.compile();
     * </code></pre>
     * 
     * @param script Jiffle source code to compile
     * 
     * @param params defines the names and roles of image variables
     *        referred to in the script.
     * 
     * @throws JiffleException if there are any errors compiling the script
     */
    public Jiffle(String script, Map<String, ImageRole> params)
            throws JiffleException {

        init();
        setScript(script);
        setImageParams(params);
        compile();
    }

    /**
     * Creates a new instance by compiling the script read from {@code scriptFile}. 
     * Using this constructor is equivalent to:
     * <pre><code>
     * Jiffle jiffle = new Jiffle();
     * jiffle.setScript(scriptFile);
     * jiffle.setImageParams(params);
     * jiffle.compile();
     * </code></pre>
     * 
     * @param scriptFile file containing the Jiffle script
     * 
     * @param params defines the names and roles of image variables
     *        referred to in the script.
     * 
     * @throws JiffleException if the file cannot be read or if there are 
     *         any errors compiling the script
     */
    public Jiffle(File scriptFile, Map<String, ImageRole> params)
            throws JiffleException, IOException {

        init();
        setScript(scriptFile);
        setImageParams(params);
        compile();
    }
    
    
    /**
     * Sets the script. Calling this method will clear any previous script
     * and runtime objects.
     * 
     * @param script a Jiffle script
     */
    public final void setScript(String script) throws JiffleException {
        if (script == null || script.trim().isEmpty()) {
            throw new JiffleException("script is empty !");
        }
        
        if (theScript != null) {
            clearCompiledObjects();
        }
        
        // add extra new line just in case last statement hits EOF
        theScript = script + "\n";
    }
    
    /**
     * Sets the script. Calling this method will clear any previous script
     * and runtime objects.
     * 
     * @param scriptFile a file containing a Jiffle script
     */
    public final void setScript(File scriptFile) throws JiffleException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(scriptFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() > 0) {
                    sb.append(line);
                    sb.append('\n');  // put the newline back on for the parser
                }
            }
            
            setScript(sb.toString());
            
        } catch (IOException ex) {
            throw new JiffleException("Could not read the script file", ex);

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
    
    /**
     * Gets the Jiffle script.
     * 
     * @return the script or an empty {@code String} if none
     *         has been set
     */
    public String getScript() {
        return theScript == null ? "" : theScript;
    }
    
    /**
     * Sets the image parameters. These define which variables in
     * the script refer to images and their types (source or destination).
     * <p>
     * This may be called before or after setting the script. No check is
     * made between script and parameters until the script is compiled.
     * 
     * @param params the image parameters
     */
    public final void setImageParams(Map<String, ImageRole> params) {
        imageParams.clear();
        imageParams.putAll(params);
    }
    
    /**
     * Gets the current image parameters. The parameters are returned
     * as an unmodifiable map.
     * 
     * @return image parameters or an empty {@code Map} if none
     *         are set
     */
    public Map<String, ImageRole> getImageParams() {
        return Collections.unmodifiableMap(imageParams);
    }

    /**
     * Replaces the default name set for this object with a user-supplied name.
     * The name is solely for use by client code. No checks are made for 
     * duplication between current instances.
     * 
     * @param name the name to assign
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the name assigned to this object. This will either be the
     * default name or one assigned by the user via {@link #setName(String)}
     */
    public String getName() {
        return name;
    }
    
    /**
     * Compiles the script into a form from which a runtime object can
     * be created.
     * 
     * @throws JiffleException if no script has been set or if any errors
     *         occur during compilation
     */
    public final void compile() throws JiffleException {
        if (theScript == null) {
            throw new JiffleException("No script has been set");
        }
        
        if (imageParams.isEmpty()) {
            throw new JiffleException("No image parameters set");
        }
        
        clearCompiledObjects();
        buildAST();

        if (!checkFunctionCalls()) {
            throw new JiffleException(getErrorString());
        }

        if (!classifyVars()) {
            throw new JiffleException(getErrorString());
        }

        transformTree();
    }
    
    /**
     * Tests whether the script has been compiled successfully.
     */
    public boolean isCompiled() {
        return (finalAST != null);
    }
    
    /**
     * Creates an instance of the default runtime class. 
     * <p>
     * The default runtime class implements {@link JiffleDirectRuntime} and
     * extends an abstract base class provided by the Jiffle compiler. Objects
     * of this class evaluate the Jiffle script and write results directly to
     * the destination image(s). Client code can call either of the methods:
     * <ul>
     * <li>{@code evaluate(int x, int y)}
     * <li>{@code evaluateAll(JiffleProgressListener listener}
     * </ul>
     * The {@code Jiffle} object must be compiled before calling this method.
     * 
     * @return the runtime object
     */
    public JiffleDirectRuntime getRuntimeInstance() throws JiffleException {
        return (JiffleDirectRuntime) createRuntimeInstance(
                EvaluationModel.DIRECT, 
                DEFAULT_DIRECT_BASE_CLASS);
    }
    
    /**
     * Creates a runtime object based using the class specified by {@code type}.
     * <p>
     * The {@code Jiffle} object must be compiled before calling this method.
     * 
     * @return the runtime object
     */
    public JiffleRuntime getRuntimeInstance(EvaluationModel type) throws JiffleException {
        switch (type) {
            case DIRECT:
                return createRuntimeInstance(type, DEFAULT_DIRECT_BASE_CLASS);
                
            case INDIRECT:
                return createRuntimeInstance(type, DEFAULT_INDIRECT_BASE_CLASS);
                
            default:
                throw new IllegalArgumentException("Invalid runtime class type: " + type);
        }
    }
    
    /**
     * Gets the runtime object for this script. 
     * <p>
     * The runtime object is an instance of {@link JiffleRuntime}. By default
     * it extends an abstract base class included with JAI-tools. An 
     * alternative base class can be specified with this method. 
     * 
     * @param recreate when {@code true} a new runtime object will be
     *        created and returned; when {@code false} a previously
     *        created object will be returned if one exists
     * 
     * @param baseClass the base class that the runtime class will extend
     * 
     * @return the runtime object
     */
    public <T extends JiffleRuntime> T getRuntimeInstance(Class<T> baseClass) throws JiffleException {
        EvaluationModel type = EvaluationModel.get(baseClass);
        if (type == null) {
            throw new JiffleException(baseClass.getName() + 
                    " does not implement a required Jiffle runtime interface");
        }
        
        return (T) createRuntimeInstance(type, baseClass);
    }
    
    /**
     * Gets a copy of the Java source for the runtime class.
     * 
     * @param scriptInDocs whether to include the original Jiffle script
     *        in the class javadocs
     * 
     * @return source for the runtime class
     */
    public String getRuntimeSource(EvaluationModel type, boolean scriptInDocs) 
            throws JiffleException {
        
        Class<? extends JiffleRuntime> baseClass = null;
        switch (type) {
            case DIRECT:
                baseClass = DEFAULT_DIRECT_BASE_CLASS;
                break;
                
            case INDIRECT:
                baseClass = DEFAULT_INDIRECT_BASE_CLASS;
        }
        return createRuntimeSource(type, baseClass, scriptInDocs);
    }

    /**
     * Initializes this object's name and runtime base class.
     */
    private void init() {
        Jiffle.refCount++ ;
        name = properties.getProperty(NAME_KEY) + refCount;
        imageParams = CollectionFactory.map();
    }
    
    /**
     * Clears all compiler and runtime objects
     */
    private void clearCompiledObjects() {
        primaryAST = null;
        finalAST = null;
        tokens = null;
        clearRuntimeObjects();
    }
    
    /**
     * Clears all runtime objects
     */
    private void clearRuntimeObjects() {
        errorReporter = null;
        errors = null;
    }
    
    /**
     * Write error messages to a string
     */
    private String getErrorString() {
        StringBuilder sb = new StringBuilder();
        for (Entry<String, ErrorCode> e : errors.entrySet()) {
            sb.append(e.getValue().toString());
            sb.append(": ");
            sb.append(e.getKey());
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Build a preliminary AST from the jiffle script. Basic syntax and grammar
     * checks are done at this stage.
     * 
     * @throws jaitools.jiffle.interpreter.JiffleException
     */
    private void buildAST() throws JiffleException {
        try {
            ANTLRStringStream input = new ANTLRStringStream(theScript);
            JiffleLexer lexer = new JiffleLexer(input);
            tokens = new CommonTokenStream(lexer);

            JiffleParser parser = new JiffleParser(tokens);
            JiffleParser.prog_return r = parser.prog();
            primaryAST = (CommonTree) r.getTree();

        } catch (RecognitionException re) {
            throw new JiffleException(
                    "error in script at or around line:" +
                    re.line + " col:" + re.charPositionInLine);
        }
    }

    /**
     * Examine function calls in the AST built by {@link #buildAST()} and
     * check that we recognize them all
     */
    private boolean checkFunctionCalls() throws JiffleException {
        FunctionValidator validator = null;
        try {
            CommonTreeNodeStream nodes = new CommonTreeNodeStream(primaryAST);
            nodes.setTokenStream(tokens);
            validator = new FunctionValidator(nodes);
            validator.start();

            if (validator.hasError()) {
                errors = validator.getErrors();
                return false;
            }
            
        } catch (RecognitionException ex) {
            // anything at this stage is probably programmer error
            throw new RuntimeException(ex);
        }
        
        return true;
    }

    /**
     * Examine variables in the AST built by {@link #buildAST()} and
     * do the following:
     * <ul>
     * <li> Identify positional and non-positional vars
     * <li> Report on vars that are used before being assigned a value
     * (all being well, these will later be tagged as input image vars
     * by {@link #validateImageVars() })
     */
    private boolean classifyVars() throws JiffleException {
        VarClassifier classifier = null;
        try {
            CommonTreeNodeStream nodes = new CommonTreeNodeStream(primaryAST);
            nodes.setTokenStream(tokens);
            classifier = new VarClassifier(nodes);
            classifier.setImageParams(imageParams);
            
            /*
             * TODO: actually do something with the ANTLR error messages in the reporter
             */
            classifier.setErrorReporter(errorReporter);
            
            classifier.start();
            
            
        } catch (CompilerExitException ex) {
            // no action required
            
        } catch (RecognitionException ex) {
            throw new JiffleException("Compilation failed: error not recognized");
            
        } finally {
            errors = classifier.getErrors();
        }

        for (ErrorCode error : errors.values()) {
            if (error.isError()) return false;
        }
        return true;
    }

    /**
     * Rewrites variable and ternary expression nodes in the AST.
     */
    private void transformTree() {
        try {
            CommonTreeNodeStream nodes = new CommonTreeNodeStream(primaryAST);
            nodes.setTokenStream(tokens);
            
            VarTransformer vt = new VarTransformer(nodes);
            vt.setImageParams(imageParams);
            VarTransformer.start_return vtResult = vt.start();
            CommonTree tree = (CommonTree) vtResult.getTree();
            
            nodes = new CommonTreeNodeStream(tree);
            nodes.setTokenStream(tokens);
            ConvertTernaryExpr cte = new ConvertTernaryExpr(nodes);
            ConvertTernaryExpr.start_return cteResult = cte.start();
            finalAST = (CommonTree) cteResult.getTree();

        } catch (RecognitionException ex) {
            throw new IllegalStateException(ex);
        }        
    }

    /**
     * Creates an instance of the runtime class. The Java source for the
     * class is created if not already cached and then compiled using
     * Janino's {@link SimpleCompiler}.
     * 
     * @throws Exception 
     */
    private JiffleRuntime createRuntimeInstance(EvaluationModel type, 
            Class<? extends JiffleRuntime> baseClass) throws JiffleException {
        if (!isCompiled()) {
            throw new JiffleException("The script has not been compiled");
        }

        String runtimeSource = createRuntimeSource(type, baseClass, false);

        try {
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.cook(runtimeSource);
            
            StringBuilder sb = new StringBuilder();
            sb.append(properties.getProperty(RUNTIME_PACKAGE_KEY)).append(".");
            
            switch (type) {
                case DIRECT:
                    sb.append(properties.getProperty(DIRECT_CLASS_KEY));
                    break;
                    
                case INDIRECT:
                    sb.append(properties.getProperty(INDIRECT_CLASS_KEY));
                    break;
                    
                default:
                    throw new IllegalArgumentException("Internal compiler error");
            }
            
            Class<?> clazz = compiler.getClassLoader().loadClass(sb.toString());

            return (JiffleRuntime) clazz.newInstance();

        } catch (Exception ex) {
            throw new JiffleException("Janino compiler failed", ex);
        }
    }
    
    /**
     * Creates the Java source code for the runtime class.
     * 
     * @param scriptInDocs whether to include the Jiffle script in the class
     *        javadocs
     * 
     * @throws JiffleException if an error occurs generating the source 
     */
    private String createRuntimeSource(EvaluationModel type, 
            Class<? extends JiffleRuntime> baseClass, boolean scriptInDocs) throws JiffleException {
        
        StringBuilder sb  = new StringBuilder();

        sb.append("package ").append(properties.getProperty(RUNTIME_PACKAGE_KEY)).append("; \n\n");
        
        String value = properties.getProperty(IMPORTS_KEY);
        if (value != null && !value.trim().isEmpty()) {
            String[] importNames = value.split(RUNTIME_IMPORTS_DELIM);
            for (String importName : importNames) {
                sb.append("import ").append(importName).append("; \n");
            }
            sb.append("\n");
        }
        
        if (scriptInDocs) {
            sb.append(formatAsJavadoc(theScript));
        }
        
        sb.append("public class ");
        
        switch (type) {
            case DIRECT:
                sb.append(properties.getProperty(DIRECT_CLASS_KEY));
                break;
                
            case INDIRECT:
                sb.append(properties.getProperty(INDIRECT_CLASS_KEY));
                break;
                
            default:
                throw new IllegalArgumentException("Internal compiler error");
        }
        
        sb.append(" extends ").append(baseClass.getName()).append(" { \n");
        sb.append(formatSource(astToJava(type), 4));
        sb.append("} \n");
        
        return sb.toString();
    }
    
    /**
     * Converts the AST to Java statements.
     * 
     * @return Java souce code
     * 
     * @throws JiffleException if an error occurs parsing the AST
     */
    private String astToJava(EvaluationModel type) throws JiffleException {
        BufferedTreeNodeStream nodes = new BufferedTreeNodeStream(finalAST);
        nodes.setTokenStream(tokens);
        ParsingErrorReporter er = new DeferredErrorReporter();
        RuntimeSourceCreator creator = null;
        
        switch (type) {
            case DIRECT:
                creator = new DirectRuntimeSourceCreator(nodes);
                break;
                
            case INDIRECT:
                creator = new IndirectRuntimeSourceCreator(nodes);
                break;
                
            default:
                throw new IllegalArgumentException("Internal compiler error");
        }
        
        try {
            creator.setErrorReporter(er);
            creator.start();
            return creator.getSource();
            
        } catch (RecognitionException ex) {
            throw new JiffleException(er.getErrors());
        }
    }
    
    /**
     * Formats the input text as a javadoc block.
     * 
     * @param text the text to format
     * 
     * @return the javadoc block
     */
    private String formatAsJavadoc(String text) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("/** \n");
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.length() > 0) {
                sb.append(" * ").append(line).append("\n");
            }
        }
        sb.append(" */ \n");
        return sb.toString();
    }
    
    /**
     * A mind-numbingly dumb code formatter.
     * 
     * @param source source code to format
     * @param baseIndent initial indentation level (number of spaces)
     * 
     * @return formatted code
     */
    private String formatSource(String source, int baseIndent) {
        StringBuilder sb = new StringBuilder();
        int indent = baseIndent;
        
        char[] spaces = new char[100];
        Arrays.fill(spaces, ' ');
        
        String[] lines = source.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("}")) indent -= 4;
            
            sb.append(spaces, 0, indent);
            sb.append(line.trim()).append("\n");
            
            if (line.endsWith("{")) indent += 4;
        }
        
        return sb.toString();
    }

}
