/*   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.xmlbeans.impl.tool;

import org.apache.xmlbeans.*;
import org.apache.xmlbeans.XmlOptions.BeanMethod;
import org.apache.xmlbeans.impl.common.*;
import org.apache.xmlbeans.impl.config.BindingConfigImpl;
import org.apache.xmlbeans.impl.repackage.Repackager;
import org.apache.xmlbeans.impl.schema.*;
import org.apache.xmlbeans.impl.util.FilerImpl;
import org.apache.xmlbeans.impl.values.XmlListImpl;
import org.apache.xmlbeans.impl.xb.xmlconfig.ConfigDocument;
import org.apache.xmlbeans.impl.xb.xmlconfig.Extensionconfig;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument;
import org.apache.xmlbeans.impl.xb.xsdschema.SchemaDocument.Schema;
import org.xml.sax.EntityResolver;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.util.*;

public class SchemaCompiler {
    public static void printUsage() {
        System.out.println("Compiles a schema into XML Bean classes and metadata.");
        System.out.println("Usage: scomp [opts] [dirs]* [schema.xsd]* [service.wsdl]* [config.xsdconfig]*");
        System.out.println("Options include:");
        System.out.println("    -cp [a;b;c] - classpath");
        System.out.println("    -d [dir] - target binary directory for .class and .xsb files");
        System.out.println("    -src [dir] - target directory for generated .java files");
        System.out.println("    -srconly - do not compile .java files or jar the output.");
        System.out.println("    -out [xmltypes.jar] - the name of the output jar");
        System.out.println("    -name - the name of the schema type - defaults to autogenerated name");
        System.out.println("    -dl - permit network downloads for imports and includes (default is off)");
        System.out.println("    -noupa - do not enforce the unique particle attribution rule");
        System.out.println("    -nopvr - do not enforce the particle valid (restriction) rule");
        System.out.println("    -noann - ignore annotations");
        System.out.println("    -novdoc - do not validate contents of <documentation>");
        System.out.println("    -noext - ignore all extension (Pre/Post and Interface) found in .xsdconfig files");
        System.out.println("    -compiler - path to external java compiler");
        System.out.println("    -ms - initial memory for external java compiler (default '" + CodeGenUtil.DEFAULT_MEM_START + "')");
        System.out.println("    -mx - maximum memory for external java compiler (default '" + CodeGenUtil.DEFAULT_MEM_MAX + "')");
        System.out.println("    -debug - compile with debug symbols");
        System.out.println("    -quiet - print fewer informational messages");
        System.out.println("    -verbose - print more informational messages");
        System.out.println("    -version - prints version information");
        System.out.println("    -license - prints license information");
        System.out.println("    -allowmdef \"[ns] [ns] [ns]\" - ignores multiple defs in given namespaces (use ##local for no-namespace)");
        System.out.println("    -catalog [file] -  catalog file for org.apache.xml.resolver.tools.CatalogResolver. (Note: needs resolver.jar from http://xml.apache.org/commons/components/resolver/index.html)");
        System.out.println("    -partialMethods [list] -  comma separated list of bean methods to be generated. Use \"-\" to negate and \"ALL\" for all." );
        System.out.println("                              processed left-to-right, e.g. \"ALL,-GET_LIST\" exclude java.util.List getters - see XmlOptions.BeanMethod" );
        System.out.println("    -repackage - repackage specification, e.g. \"org.apache.xmlbeans.metadata:mypackage.metadata\" to change the metadata directory");
        System.out.println("    -copyann - copy schema annotations to javadoc (default false) - don't activate on untrusted schema sources!");
        System.out.println("    -sourcecodeencoding - Generate Java source code and jar package using utf-8");
        System.out.println("    -usejavashortname - Generate file name using JavaShortName");
        /* Undocumented feature - pass in one schema compiler extension and related parameters
        System.out.println("    -extension - registers a schema compiler extension");
        System.out.println("    -extensionParms - specify parameters for the compiler extension");
        System.out.println("    -schemaCodePrinter - specify SchemaCodePrinter class");
        */
        System.out.println();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(0);
            return;
        }

        Set<String> flags = new HashSet<>();
        flags.add("h");
        flags.add("help");
        flags.add("usage");
        flags.add("license");
        flags.add("quiet");
        flags.add("verbose");
        flags.add("version");
        flags.add("dl");
        flags.add("noupa");
        flags.add("nopvr");
        flags.add("noann");
        flags.add("novdoc");
        flags.add("noext");
        flags.add("srconly");
        flags.add("debug");
        flags.add("sourcecodeencoding");
        flags.add("usejavashortname");

        Set<String> opts = new HashSet<>();
        opts.add("out");
        opts.add("name");
        opts.add("src");
        opts.add("d");
        opts.add("cp");
        opts.add("compiler");
        opts.add("jar"); // deprecated
        opts.add("ms");
        opts.add("mx");
        opts.add("repackage");
        opts.add("schemaCodePrinter");
        opts.add("extension");
        opts.add("extensionParms");
        opts.add("allowmdef");
        opts.add("catalog");
        opts.add("partialMethods");
        opts.add("copyann");

        CommandLine cl = new CommandLine(args, flags, opts);

        if (cl.getOpt("h") != null || cl.getOpt("help") != null || cl.getOpt("usage") != null) {
            printUsage();
            System.exit(0);
            return;
        }

        String[] badopts = cl.getBadOpts();
        if (badopts.length > 0) {
            for (String badopt : badopts) {
                System.out.println("Unrecognized option: " + badopt);
            }
            printUsage();
            System.exit(0);
            return;
        }

        if (cl.getOpt("license") != null) {
            CommandLine.printLicense();
            System.exit(0);
            return;
        }

        if (cl.getOpt("version") != null) {
            CommandLine.printVersion();
            System.exit(0);
            return;
        }

        boolean verbose = (cl.getOpt("verbose") != null);
        boolean quiet = (cl.getOpt("quiet") != null);
        if (verbose) {
            quiet = false;
        }

        if (verbose) {
            CommandLine.printVersion();
        }

        String outputfilename = cl.getOpt("out");

        String repackage = cl.getOpt("repackage");

        String codePrinterClass = cl.getOpt("schemaCodePrinter");
        SchemaCodePrinter codePrinter = null;
        if (codePrinterClass != null) {
            try {
                codePrinter = (SchemaCodePrinter)
                    Class.forName(codePrinterClass).getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.err.println
                    ("Failed to load SchemaCodePrinter class " +
                     codePrinterClass + "; proceeding with default printer");
            }
        }

        String name = cl.getOpt("name");

        boolean download = (cl.getOpt("dl") != null);
        boolean noUpa = (cl.getOpt("noupa") != null);
        boolean noPvr = (cl.getOpt("nopvr") != null);
        boolean noAnn = (cl.getOpt("noann") != null);
        boolean noVDoc = (cl.getOpt("novdoc") != null);
        boolean noExt = (cl.getOpt("noext") != null);
        boolean nojavac = (cl.getOpt("srconly") != null);
        boolean debug = (cl.getOpt("debug") != null);
        boolean copyAnn = (cl.getOpt("copyann") != null);
        String sourceCodeEncoding = cl.getOpt("sourcecodeencoding");
        boolean useJavaShortName = (cl.getOpt("usejavashortname") != null);

        String allowmdef = cl.getOpt("allowmdef");
        Set<String> mdefNamespaces = (allowmdef == null ? Collections.emptySet() :
            new HashSet<>(Arrays.asList(XmlListImpl.split_list(allowmdef))));

        List<Extension> extensions = new ArrayList<>();
        if (cl.getOpt("extension") != null) {
            try {
                Extension e = new Extension();
                e.setClassName(Class.forName(cl.getOpt("extension"), false, Thread.currentThread().getContextClassLoader()));
                extensions.add(e);
            } catch (ClassNotFoundException e) {
                System.err.println("Could not find extension class: " + cl.getOpt("extension") + "  Is it on your classpath?");
                System.exit(1);
            }
        }

        if (extensions.size() > 0) {
            // example: -extensionParms typeMappingFileLocation=d:\types
            if (cl.getOpt("extensionParms") != null) {
                Extension e = extensions.get(0);
                // extensionParms are delimited by ';'
                StringTokenizer parmTokens = new StringTokenizer(cl.getOpt("extensionParms"), ";");
                while (parmTokens.hasMoreTokens()) {
                    // get name value pair for each extension parms and stick into extension parms
                    String nvPair = parmTokens.nextToken();
                    int index = nvPair.indexOf('=');
                    if (index < 0) {
                        System.err.println("extensionParms should be name=value;name=value");
                        System.exit(1);
                    }
                    String n = nvPair.substring(0, index);
                    String v = nvPair.substring(index + 1);
                    Extension.Param param = e.createParam();
                    param.setName(n);
                    param.setValue(v);
                }
            }
        }

        String classesdir = cl.getOpt("d");
        File classes = null;
        if (classesdir != null) {
            classes = new File(classesdir);
        }

        String srcdir = cl.getOpt("src");
        File src = null;
        if (srcdir != null) {
            src = new File(srcdir);
        }
        if (nojavac && srcdir == null && classes != null) {
            src = classes;
        }

        // create temp directory
        File tempdir = null;
        if (src == null || classes == null) {
            try {
                tempdir = SchemaCodeGenerator.createTempDir();
            } catch (java.io.IOException e) {
                System.err.println("Error creating temp dir " + e);
                System.exit(1);
            }
        }

        File jarfile = null;
        if (outputfilename == null && classes == null && !nojavac) {
            outputfilename = "xmltypes.jar";
        }
        if (outputfilename != null) {
            jarfile = new File(outputfilename);
        }

        if (src == null) {
            src = IOUtil.createDir(tempdir, "src");
        }
        if (classes == null) {
            classes = IOUtil.createDir(tempdir, "classes");
        }

        File[] classpath;
        String cpString = cl.getOpt("cp");
        if (cpString != null) {
            String[] cpparts = cpString.split(File.pathSeparator);
            List<File> cpList = new ArrayList<>();
            for (String cppart : cpparts) {
                cpList.add(new File(cppart));
            }
            classpath = cpList.toArray(new File[0]);
        } else {
            classpath = CodeGenUtil.systemClasspath();
        }

        String compiler = cl.getOpt("compiler");
        String jar = cl.getOpt("jar");
        if (verbose && jar != null) {
            System.out.println("The 'jar' option is no longer supported.");
        }

        String memoryInitialSize = cl.getOpt("ms");
        String memoryMaximumSize = cl.getOpt("mx");

        File[] xsdFiles = cl.filesEndingWith(".xsd");
        File[] wsdlFiles = cl.filesEndingWith(".wsdl");
        File[] javaFiles = cl.filesEndingWith(".java");
        File[] configFiles = cl.filesEndingWith(".xsdconfig");
        URL[] urlFiles = cl.getURLs();

        if (xsdFiles.length + wsdlFiles.length + urlFiles.length == 0) {
            System.out.println("Could not find any xsd or wsdl files to process.");
            System.exit(0);
        }
        File baseDir = cl.getBaseDir();
        URI baseURI = baseDir == null ? null : baseDir.toURI();

        XmlErrorPrinter err = new XmlErrorPrinter(verbose, baseURI);

        String catString = cl.getOpt("catalog");

        String partialMethods = cl.getOpt("partialMethods");

        Parameters params = new Parameters();
        params.setBaseDir(baseDir);
        params.setXsdFiles(xsdFiles);
        params.setWsdlFiles(wsdlFiles);
        params.setJavaFiles(javaFiles);
        params.setConfigFiles(configFiles);
        params.setUrlFiles(urlFiles);
        params.setClasspath(classpath);
        params.setOutputJar(jarfile);
        params.setName(name);
        params.setSrcDir(src);
        params.setClassesDir(classes);
        params.setCompiler(compiler);
        params.setMemoryInitialSize(memoryInitialSize);
        params.setMemoryMaximumSize(memoryMaximumSize);
        params.setNojavac(nojavac);
        params.setQuiet(quiet);
        params.setVerbose(verbose);
        params.setDownload(download);
        params.setNoUpa(noUpa);
        params.setNoPvr(noPvr);
        params.setNoAnn(noAnn);
        params.setNoVDoc(noVDoc);
        params.setNoExt(noExt);
        params.setDebug(debug);
        params.setSourceCodeEncoding(sourceCodeEncoding);
        params.setUseShortName(useJavaShortName);
        params.setErrorListener(err);
        params.setRepackage(repackage);
        params.setExtensions(extensions);
        params.setMdefNamespaces(mdefNamespaces);
        params.setCatalogFile(catString);
        params.setSchemaCodePrinter(codePrinter);
        params.setPartialMethods(parsePartialMethods(partialMethods));
        params.setCopyAnn(copyAnn);
        boolean result = compile(params);

        if (tempdir != null) {
            SchemaCodeGenerator.tryHardToDelete(tempdir);
        }

        if (!result) {
            System.exit(1);
        }

        System.exit(0);
    }

    private static SchemaTypeSystem loadTypeSystem(String name, File[] xsdFiles, File[] wsdlFiles, URL[] urlFiles, File[] configFiles,
                                                   File[] javaFiles, ResourceLoader cpResourceLoader,
                                                   boolean download, boolean noUpa, boolean noPvr, boolean noAnn, boolean noVDoc, boolean noExt, String sourceCodeEncoding, boolean useShortName,
                                                   Set<String> mdefNamespaces, File baseDir, Map<String, String> sourcesToCopyMap,
                                                   Collection<XmlError> outerErrorListener, File schemasDir, EntityResolver entResolver, File[] classpath) {
        XmlErrorWatcher errorListener = new XmlErrorWatcher(outerErrorListener);

        // construct the state (have to initialize early in case of errors)
        StscState state = StscState.start();
        try {
            state.setErrorListener(errorListener);

            // For parsing XSD and WSDL files, we should use the SchemaDocument
            // classloader rather than the thread context classloader.  This is
            // because in some situations (such as when being invoked by ant
            // underneath the ide) the context classloader is potentially weird
            // (because of the design of ant).

            SchemaTypeLoader loader = XmlBeans.typeLoaderForClassLoader(SchemaDocument.class.getClassLoader());

            // step 1, parse all the XSD files.
            ArrayList<Schema> scontentlist = new ArrayList<>();
            if (xsdFiles != null) {
                for (File xsdFile : xsdFiles) {
                    try {
                        XmlOptions options = new XmlOptions();
                        options.setLoadLineNumbers();
                        options.setLoadMessageDigest();
                        options.setEntityResolver(entResolver);

                        XmlObject schemadoc = loader.parse(xsdFile, null, options);
                        if (!(schemadoc instanceof SchemaDocument)) {
                            StscState.addError(errorListener, XmlErrorCodes.INVALID_DOCUMENT_TYPE,
                                new Object[]{xsdFile, "schema"}, schemadoc);
                        } else {
                            addSchema(xsdFile.toString(), (SchemaDocument) schemadoc,
                                errorListener, noVDoc, scontentlist);
                        }
                    } catch (XmlException e) {
                        errorListener.add(e.getError());
                    } catch (Exception e) {
                        StscState.addError(errorListener, XmlErrorCodes.CANNOT_LOAD_FILE,
                            new Object[]{"xsd", xsdFile, e.getMessage()}, xsdFile);
                    }
                }
            }

            // step 2, parse all WSDL files
            if (wsdlFiles != null) {
                for (File wsdlFile : wsdlFiles) {
                    try {
                        XmlOptions options = new XmlOptions();
                        options.setLoadLineNumbers();
                        options.setLoadSubstituteNamespaces(Collections.singletonMap(
                            "http://schemas.xmlsoap.org/wsdl/", "http://www.apache.org/internal/xmlbeans/wsdlsubst"
                        ));
                        options.setEntityResolver(entResolver);

                        XmlObject wsdldoc = loader.parse(wsdlFile, null, options);

                        if (!(wsdldoc instanceof org.apache.xmlbeans.impl.xb.substwsdl.DefinitionsDocument)) {
                            StscState.addError(errorListener, XmlErrorCodes.INVALID_DOCUMENT_TYPE,
                                new Object[]{wsdlFile, "wsdl"}, wsdldoc);
                        } else {
                            addWsdlSchemas(wsdlFile.toString(), (org.apache.xmlbeans.impl.xb.substwsdl.DefinitionsDocument) wsdldoc, errorListener, noVDoc, scontentlist);
                        }
                    } catch (XmlException e) {
                        errorListener.add(e.getError());
                    } catch (Exception e) {
                        StscState.addError(errorListener, XmlErrorCodes.CANNOT_LOAD_FILE,
                            new Object[]{"wsdl", wsdlFile, e.getMessage()}, wsdlFile);
                    }
                }
            }

            // step 3, parse all URL files
            // XMLBEANS-58 - Ability to pass URLs instead of Files for Wsdl/Schemas
            if (urlFiles != null) {
                for (URL urlFile : urlFiles) {
                    try {
                        XmlOptions options = new XmlOptions();
                        options.setLoadLineNumbers();
                        options.setLoadSubstituteNamespaces(Collections.singletonMap("http://schemas.xmlsoap.org/wsdl/", "http://www.apache.org/internal/xmlbeans/wsdlsubst"));
                        options.setEntityResolver(entResolver);

                        XmlObject urldoc = loader.parse(urlFile, null, options);

                        if ((urldoc instanceof org.apache.xmlbeans.impl.xb.substwsdl.DefinitionsDocument)) {
                            addWsdlSchemas(urlFile.toString(), (org.apache.xmlbeans.impl.xb.substwsdl.DefinitionsDocument) urldoc, errorListener, noVDoc, scontentlist);
                        } else if ((urldoc instanceof SchemaDocument)) {
                            addSchema(urlFile.toString(), (SchemaDocument) urldoc,
                                errorListener, noVDoc, scontentlist);
                        } else {
                            StscState.addError(errorListener, XmlErrorCodes.INVALID_DOCUMENT_TYPE,
                                new Object[]{urlFile, "wsdl or schema"}, urldoc);
                        }

                    } catch (XmlException e) {
                        errorListener.add(e.getError());
                    } catch (Exception e) {
                        StscState.addError(errorListener, XmlErrorCodes.CANNOT_LOAD_FILE,
                            new Object[]{"url", urlFile, e.getMessage()}, urlFile);
                    }
                }
            }

            Schema[] sdocs = scontentlist.toArray(new Schema[0]);

            // now the config files.
            ArrayList<ConfigDocument.Config> cdoclist = new ArrayList<>();
            if (configFiles != null) {
                if (noExt) {
                    System.out.println("Pre/Post and Interface extensions will be ignored.");
                }

                for (File configFile : configFiles) {
                    try {
                        XmlOptions options = new XmlOptions();
                        options.setLoadLineNumbers();
                        options.setEntityResolver(entResolver);
                        options.setLoadSubstituteNamespaces(MAP_COMPATIBILITY_CONFIG_URIS);

                        XmlObject configdoc = loader.parse(configFile, null, options);
                        if (!(configdoc instanceof ConfigDocument)) {
                            StscState.addError(errorListener, XmlErrorCodes.INVALID_DOCUMENT_TYPE,
                                new Object[]{configFile, "xsd config"}, configdoc);
                        } else {
                            StscState.addInfo(errorListener, "Loading config file " + configFile);
                            if (configdoc.validate(new XmlOptions().setErrorListener(errorListener))) {
                                ConfigDocument.Config config = ((ConfigDocument) configdoc).getConfig();
                                cdoclist.add(config);
                                if (noExt) {
                                    //disable extensions
                                    config.setExtensionArray(new Extensionconfig[]{});
                                }
                            }
                        }
                    } catch (XmlException e) {
                        errorListener.add(e.getError());
                    } catch (Exception e) {
                        StscState.addError(errorListener, XmlErrorCodes.CANNOT_LOAD_FILE,
                            new Object[]{"xsd config", configFile, e.getMessage()}, configFile);
                    }
                }
            }
            ConfigDocument.Config[] cdocs = cdoclist.toArray(new ConfigDocument.Config[0]);

            SchemaTypeLoader linkTo = SchemaTypeLoaderImpl.build(null, cpResourceLoader, null);

            URI baseURI = null;
            if (baseDir != null) {
                baseURI = baseDir.toURI();
            }

            XmlOptions opts = new XmlOptions();
            if (download) {
                opts.setCompileDownloadUrls();
            }
            if (noUpa) {
                opts.setCompileNoUpaRule();
            }
            if (noPvr) {
                opts.setCompileNoPvrRule();
            }
            if (noAnn) {
                opts.setCompileNoAnnotations();
            }
            if (sourceCodeEncoding != null ) {
                opts.setCharacterEncoding(sourceCodeEncoding);
            }
            if (useShortName) {
                opts.setCompileUseShortJavaName();
            }
            if (mdefNamespaces != null) {
                opts.setCompileMdefNamespaces(mdefNamespaces);
            }
            opts.setCompileNoValidation(); // already validated here
            opts.setEntityResolver(entResolver);

            // now pass it to the main compile function
            SchemaTypeSystemCompiler.Parameters params = new SchemaTypeSystemCompiler.Parameters();
            params.setName(name);
            params.setSchemas(sdocs);
            params.setConfig(BindingConfigImpl.forConfigDocuments(cdocs, javaFiles, classpath));
            params.setLinkTo(linkTo);
            params.setOptions(opts);
            params.setErrorListener(errorListener);
            params.setJavaize(true);
            params.setBaseURI(baseURI);
            params.setSourcesToCopyMap(sourcesToCopyMap);
            params.setSchemasDir(schemasDir);
            return SchemaTypeSystemCompiler.compile(params);
        } finally {
            StscState.end();
        }
    }

    private static void addSchema(String name, SchemaDocument schemadoc,
                                  XmlErrorWatcher errorListener, boolean noVDoc, List<Schema> scontentlist) {
        StscState.addInfo(errorListener, "Loading schema file " + name);
        XmlOptions opts = new XmlOptions().setErrorListener(errorListener);
        if (noVDoc) {
            opts.setValidateTreatLaxAsSkip();
        }
        if (schemadoc.validate(opts)) {
            scontentlist.add((schemadoc).getSchema());
        }
    }

    private static void addWsdlSchemas(String name,
                                       org.apache.xmlbeans.impl.xb.substwsdl.DefinitionsDocument wsdldoc,
                                       XmlErrorWatcher errorListener, boolean noVDoc, List<Schema> scontentlist) {
        if (wsdlContainsEncoded(wsdldoc)) {
            StscState.addWarning(errorListener, "The WSDL " + name + " uses SOAP encoding. SOAP encoding is not compatible with literal XML Schema.", XmlErrorCodes.GENERIC_ERROR, wsdldoc);
        }
        StscState.addInfo(errorListener, "Loading wsdl file " + name);
        XmlOptions opts = new XmlOptions().setErrorListener(errorListener);
        if (noVDoc) {
            opts.setValidateTreatLaxAsSkip();
        }
        XmlObject[] types = wsdldoc.getDefinitions().getTypesArray();
        int count = 0;
        for (XmlObject type : types) {
            XmlObject[] schemas = type.selectPath("declare namespace xs=\"http://www.w3.org/2001/XMLSchema\" xs:schema");
            if (schemas.length == 0) {
                StscState.addWarning(errorListener, "The WSDL " + name + " did not have any schema documents in namespace 'http://www.w3.org/2001/XMLSchema'", XmlErrorCodes.GENERIC_ERROR, wsdldoc);
                continue;
            }

            for (XmlObject schema : schemas) {
                if (schema.validate(opts)) {
                    count++;
                    scontentlist.add((Schema)schema);
                }
            }
        }
        StscState.addInfo(errorListener, "Processing " + count + " schema(s) in " + name);
    }

    public static boolean compile(Parameters params) {
        File baseDir = params.getBaseDir();
        File[] xsdFiles = params.getXsdFiles();
        File[] wsdlFiles = params.getWsdlFiles();
        URL[] urlFiles = params.getUrlFiles();
        File[] javaFiles = params.getJavaFiles();
        File[] configFiles = params.getConfigFiles();
        File[] classpath = params.getClasspath();
        File outputJar = params.getOutputJar();
        String name = params.getName();
        File srcDir = params.getSrcDir();
        File classesDir = params.getClassesDir();
        String compiler = params.getCompiler();
        String memoryInitialSize = params.getMemoryInitialSize();
        String memoryMaximumSize = params.getMemoryMaximumSize();
        boolean nojavac = params.isNojavac();
        boolean debug = params.isDebug();
        boolean verbose = params.isVerbose();
        boolean quiet = params.isQuiet();
        boolean download = params.isDownload();
        boolean noUpa = params.isNoUpa();
        boolean noPvr = params.isNoPvr();
        boolean noAnn = params.isNoAnn();
        boolean noVDoc = params.isNoVDoc();
        boolean noExt = params.isNoExt();
        boolean incrSrcGen = params.isIncrementalSrcGen();
        boolean copyAnn = params.isCopyAnn();
        String sourceCodeEncoding = params.getSourceCodeEncoding();
        boolean useShortName = params.isUseShortName();
        Collection<XmlError> outerErrorListener = params.getErrorListener();
        Set<BeanMethod> partialMethods = params.getPartialMethods();

        String repackage = params.getRepackage();

        if (repackage != null) {
            SchemaTypeLoaderImpl.METADATA_PACKAGE_LOAD = SchemaTypeSystemImpl.METADATA_PACKAGE_GEN;

            Repackager repackager = new Repackager(repackage);

            StringBuffer sb = new StringBuffer(SchemaTypeLoaderImpl.METADATA_PACKAGE_LOAD);
            sb = repackager.repackage(sb);
            SchemaTypeSystemImpl.METADATA_PACKAGE_GEN = sb.toString();

            System.out.println("SchemaCompiler  Metadata LOAD:" + SchemaTypeLoaderImpl.METADATA_PACKAGE_LOAD + " GEN:" + SchemaTypeSystemImpl.METADATA_PACKAGE_GEN);
        }

        SchemaCodePrinter codePrinter = params.getSchemaCodePrinter();
        List<Extension> extensions = params.getExtensions();
        Set<String> mdefNamespaces = params.getMdefNamespaces();

        EntityResolver cmdLineEntRes = params.getEntityResolver() == null ?
            ResolverUtil.resolverForCatalog(params.getCatalogFile()) : params.getEntityResolver();

        if (srcDir == null || classesDir == null) {
            throw new IllegalArgumentException("src and class gen directories may not be null.");
        }

        long start = System.currentTimeMillis();

        // Calculate the basenames based on the relativized filenames on the filesystem
        if (baseDir == null) {
            String userDir = SystemProperties.getProperty("user.dir");
            assert (userDir != null);
            baseDir = new File(userDir);
        }

        ResourceLoader cpResourceLoader = null;

        Map<String, String> sourcesToCopyMap = new HashMap<>();

        if (classpath != null) {
            cpResourceLoader = new PathResourceLoader(classpath);
        }

        boolean result = true;

        File schemasDir = IOUtil.createDir(classesDir, SchemaTypeSystemImpl.METADATA_PACKAGE_GEN + "/src");

        // build the in-memory type system
        XmlErrorWatcher errorListener = new XmlErrorWatcher(outerErrorListener);
        SchemaTypeSystem system = loadTypeSystem(name, xsdFiles, wsdlFiles, urlFiles, configFiles,
            javaFiles, cpResourceLoader, download, noUpa, noPvr, noAnn, noVDoc, noExt, sourceCodeEncoding, useShortName, mdefNamespaces,
            baseDir, sourcesToCopyMap, errorListener, schemasDir, cmdLineEntRes, classpath);
        if (errorListener.hasError()) {
            result = false;
        }
        long finish = System.currentTimeMillis();
        if (!quiet) {
            System.out.println("Time to build schema type system: " + ((double) (finish - start) / 1000.0) + " seconds");
        }

        // now code generate and compile the JAR
        if (result && system != null) // todo: don't check "result" here if we want to compile anyway, ignoring invalid schemas
        {
            start = System.currentTimeMillis();

            // filer implementation writes binary .xsd and generated source to disk
            Repackager repackager = (repackage == null ? null : new Repackager(repackage));
            FilerImpl filer = new FilerImpl(classesDir, srcDir, repackager, verbose, incrSrcGen);

            // currently just for schemaCodePrinter
            XmlOptions options = new XmlOptions();
            if (codePrinter != null) {
                options.setSchemaCodePrinter(codePrinter);
            }
            options.setCompilePartialMethod(partialMethods);
            options.setCompileNoAnnotations(noAnn);
            options.setCompileAnnotationAsJavadoc(copyAnn);
            options.setCharacterEncoding(sourceCodeEncoding);
            options.setCompileUseShortJavaName(useShortName);

            // save .xsb files
            system.save(filer);

            // gen source files
            result = SchemaTypeSystemCompiler.generateTypes(system, filer, options);

            if (incrSrcGen) {
                // We have to delete extra source files that may be out of date
                SchemaCodeGenerator.deleteObsoleteFiles(srcDir, srcDir,
                    new HashSet<>(filer.getSourceFiles()));
            }

            if (result) {
                finish = System.currentTimeMillis();
                if (!quiet) {
                    System.out.println("Time to generate code: " + ((double) (finish - start) / 1000.0) + " seconds");
                }
            }

            // compile source
            if (result && !nojavac) {
                start = System.currentTimeMillis();

                List<File> sourcefiles = filer.getSourceFiles();

                if (javaFiles != null) {
                    sourcefiles.addAll(java.util.Arrays.asList(javaFiles));
                }
                if (!CodeGenUtil.externalCompile(sourcefiles, classesDir, classpath, debug, compiler, null,
                    memoryInitialSize, memoryMaximumSize, quiet, verbose, sourceCodeEncoding)) {
                    result = false;
                }

                finish = System.currentTimeMillis();
                if (result && !params.isQuiet()) {
                    System.out.println("Time to compile code: " + ((double) (finish - start) / 1000.0) + " seconds");
                }

                // jar classes and .xsb
                if (result && outputJar != null) {
                    try {
                        new JarHelper().jarDir(classesDir, outputJar);
                    } catch (IOException e) {
                        System.err.println("IO Error " + e);
                        result = false;
                    }

                    if (result && !params.isQuiet()) {
                        System.out.println("Compiled types to: " + outputJar);
                    }
                }
            }
        }

        if (!result && !quiet) {
            System.out.println("BUILD FAILED");
        } else {
            // call schema compiler extension if registered
            runExtensions(extensions, system, classesDir);
        }

        if (cpResourceLoader != null) {
            cpResourceLoader.close();
        }
        return result;
    }

    static Set<BeanMethod> parsePartialMethods(String partialMethods) {
        final Set<BeanMethod> beanMethods = new HashSet<>();
        if (partialMethods != null) {
            for (String pm : partialMethods.split(",")) {
                if ("ALL".equals(pm)) {
                    beanMethods.addAll(Arrays.asList(BeanMethod.values()));
                    continue;
                }
                boolean neg = pm.startsWith("-");
                BeanMethod bm = BeanMethod.valueOf(pm.substring(neg ? 1 : 0));
                if (neg) {
                    beanMethods.remove(bm);
                } else {
                    beanMethods.add(bm);
                }
            }
        }
        return beanMethods.isEmpty() ? null : beanMethods;
    }

    private static void runExtensions(List<Extension> extensions, SchemaTypeSystem system, File classesDir) {
        if (extensions != null && extensions.size() > 0) {
            SchemaCompilerExtension sce;
            Iterator<Extension> i = extensions.iterator();
            Map<String, String> extensionParms;
            String classesDirName;
            try {
                classesDirName = classesDir.getCanonicalPath();
            } catch (java.io.IOException e) {
                System.out.println("WARNING: Unable to get the path for schema jar file");
                classesDirName = classesDir.getAbsolutePath();
            }

            while (i.hasNext()) {
                Extension extension = i.next();
                try {
                    sce = (SchemaCompilerExtension) extension.getClassName().getDeclaredConstructor().newInstance();
                } catch (NoSuchMethodException | InstantiationException e) {
                    System.out.println("UNABLE to instantiate schema compiler extension:" + extension.getClassName().getName());
                    System.out.println("EXTENSION Class was not run");
                    break;
                } catch (InvocationTargetException | IllegalAccessException e) {
                    System.out.println("ILLEGAL ACCESS Exception when attempting to instantiate schema compiler extension: " + extension.getClassName().getName());
                    System.out.println("EXTENSION Class was not run");
                    break;
                }

                System.out.println("Running Extension: " + sce.getExtensionName());
                extensionParms = new HashMap<>();
                for (Extension.Param p : extension.getParams()) {
                    extensionParms.put(p.getName(), p.getValue());
                }
                extensionParms.put("classesDir", classesDirName);
                sce.schemaCompilerExtension(system, extensionParms);
            }
        }
    }


    private static boolean wsdlContainsEncoded(XmlObject wsdldoc) {
        // search for any <soap:body use="encoded"/> etc.
        XmlObject[] useAttrs = wsdldoc.selectPath(
            "declare namespace soap='http://schemas.xmlsoap.org/wsdl/soap/' " +
            ".//soap:body/@use|.//soap:header/@use|.//soap:fault/@use");
        for (XmlObject useAttr : useAttrs) {
            if ("encoded".equals(((SimpleValue) useAttr).getStringValue())) {
                return true;
            }
        }
        return false;
    }

    private static final String CONFIG_URI = "http://xml.apache.org/xmlbeans/2004/02/xbean/config";
    private static final String COMPATIBILITY_CONFIG_URI = "http://www.bea.com/2002/09/xbean/config";
    private static final Map<String, String> MAP_COMPATIBILITY_CONFIG_URIS
        = Collections.singletonMap(COMPATIBILITY_CONFIG_URI, CONFIG_URI);
}
