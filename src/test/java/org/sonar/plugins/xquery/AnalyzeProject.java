/*
 * © 2013 by Intellectual Reserve, Inc. All rights reserved.
 */

package org.sonar.plugins.xquery;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.runtime.ANTLRFileStream;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.TokenStream;
import org.antlr.runtime.debug.ParseTreeBuilder;
import org.antlr.runtime.debug.XQueryParseTreeBuilder;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.plexus.util.FileUtils;
import org.sonar.api.resources.InputFile;
import org.sonar.api.resources.InputFileUtils;
import org.sonar.api.resources.ProjectFileSystem;
import org.sonar.api.resources.Resource;
import org.sonar.api.rules.ActiveRule;
import org.sonar.api.rules.ActiveRuleParam;
import org.sonar.api.rules.AnnotationRuleParser;
import org.sonar.api.rules.Rule;
import org.sonar.api.rules.Violation;
import org.sonar.api.utils.AnnotationUtils;
import org.sonar.api.utils.SonarException;
import org.sonar.plugins.xquery.api.XQueryConstants;
import org.sonar.plugins.xquery.checks.AbstractCheck;
import org.sonar.plugins.xquery.language.SourceCode;
import org.sonar.plugins.xquery.language.XQueryFile;
import org.sonar.plugins.xquery.language.XQueryLineCountParser;
import org.sonar.plugins.xquery.language.XQuerySourceCode;
import org.sonar.plugins.xquery.parser.LazyTokenStream;
import org.sonar.plugins.xquery.parser.XQueryLexer;
import org.sonar.plugins.xquery.parser.XQueryParser;
import org.sonar.plugins.xquery.parser.XQueryTree;
import org.sonar.plugins.xquery.parser.XQueryTreeAdaptor;
import org.sonar.plugins.xquery.parser.node.DependencyMapper;
import org.sonar.plugins.xquery.parser.reporter.ProblemReporter;
import org.sonar.plugins.xquery.parser.visitor.XQueryAstParser;
import org.sonar.plugins.xquery.parser.visitor.XQueryAstVisitor;
import org.sonar.plugins.xquery.rules.CheckClasses;
import org.sonar.plugins.xquery.rules.XQueryRulesRepository;

public class AnalyzeProject {

    public static String CODE_ROOT = "/home/cieslinskice/Documents/Code/marklogic-extensions/performance";
    public static File BASE_DIR = new File(CODE_ROOT);
    
    // Comma-delimited (no spaces) list of includes/excludes for the different phases
    public static String MAPPING_INCLUDES = "**/*.xqy";
    public static String MAPPING_EXCLUDES = "**/target/**";
    public static String PROCESS_INCLUDES = "**/data/start-monitor.xqy";
    public static String PROCESS_EXCLUDES = "**/target/**";
    
    // Regular expression of list of rules to evaluate (.* for all)
    public static String RULE_INCLUDES = "EffectiveBoolean.*";
    public static List<File> SOURCE_DIRS = Arrays.asList(new File[] { new File(CODE_ROOT + "/src/main/xquery") });

    public static void main(String[] args) throws IOException {
        long start = new Date().getTime();
        File directory = new File(CODE_ROOT);
        System.out.println("Analyzing files in " + CODE_ROOT);  
        List<XQueryAstVisitor> visitors = new ArrayList<XQueryAstVisitor>();        

        // Create a mapper and add it to the visitors so that it can keep track
        // of global declarations and the local declaration stack
        DependencyMapper mapper = new DependencyMapper();
        visitors.add(mapper);
        
        // Set up output directories
        String treeDirectory = System.getProperty("java.io.tmpdir") + "/rule-analysis";
        String outputFile = treeDirectory + "/output.txt";
        FileUtils.mkdir(treeDirectory);
        FileUtils.cleanDirectory(treeDirectory);

        // Add all the rules to check
        List<Rule> rules = new XQueryRulesRepository(new AnnotationRuleParser()).createRules();
        for (Rule rule : rules) {
            String key = rule.getKey();
            
            Pattern p = Pattern.compile(RULE_INCLUDES);
            Matcher m = p.matcher(key);
            if (m.matches()) {
                Class<AbstractCheck> checkClass = getCheckClass(rule);
                if (checkClass != null) {
                    visitors.add(createCheck(checkClass, rule));
                }        
            }
        }

        // Do the first pass to map all the global dependencies
        System.out.println("Scanning all files to map dependencies");
        for (File file: (List<File>) FileUtils.getFiles(directory, MAPPING_INCLUDES, MAPPING_EXCLUDES)) {
            if (file.exists()) {
                try {
                    SourceCode sourceCode = new XQuerySourceCode(new XQueryFile(file.getAbsolutePath()), org.apache.commons.io.FileUtils.readLines(file, "UTF-8"));
                    System.out.println("----- Mapping " + file.getAbsolutePath() + " -----");
                    FileUtils.fileAppend(outputFile, "\n----- Mapping " + file.getAbsolutePath() + " -----");
    
                    XQueryAstParser parser = new XQueryAstParser(sourceCode, Arrays.asList(new XQueryAstVisitor[] { mapper }));
                    XQueryTree tree = parser.parse();
                    parser.mapDependencies(tree, mapper);
                } catch (Exception e) {
                    System.out.println("Could not map the dependencies in the file " + file.getAbsolutePath());
                    FileUtils.fileAppend(outputFile, "\nCould not map the dependencies in the file " + file.getAbsolutePath());
                    e.printStackTrace();
                }
            }
        }
        
        long now = new Date().getTime();
        System.out.println("Mapping finished in " + (now - start) + " ms");
        FileUtils.fileAppend(outputFile, "\nMapping finished in " + (now - start) + " ms");
       
        // Now that the global mappings are done we can change the mode to "local"
        mapper.setMode("local");

        // Do the second pass to process the checks and other metrics
        System.out.println("Scanning all files and gathering metrics");
        for (File file:  (List<File>) FileUtils.getFiles(directory, PROCESS_INCLUDES, PROCESS_EXCLUDES)) {
            if (file.exists()) {    
                try {
                    SourceCode sourceCode = new XQuerySourceCode(new XQueryFile(file.getAbsolutePath()), org.apache.commons.io.FileUtils.readLines(file, "UTF-8"));
                    System.out.println("----- Analyzing " + file.getAbsolutePath() + " -----");
                    FileUtils.fileAppend(outputFile, "\n----- Analyzing " + file.getAbsolutePath() + " -----");
    
                    ProblemReporter reporter = new ProblemReporter();
                    XQueryAstParser parser = new XQueryAstParser(sourceCode, visitors);                    
                    XQueryTree tree = parser.parse(reporter);
                    parser.process(tree, mapper, reporter);
    
                    // Output the violations
                    for (Violation violation : sourceCode.getViolations()) {
                        System.out.println("      - " + violation.getRule().getSeverity() + " Violation on line " + violation.getLineId() + ": " + violation.getRule().getName());
                        FileUtils.fileAppend(outputFile, "\n      - " + violation.getRule().getSeverity() + " Violation on line " + violation.getLineId() + ": " + violation.getRule().getName());
                    }
                } catch (Exception e) {
                    System.out.println("Could not analyze the file " + file.getAbsolutePath());
                    FileUtils.fileAppend(outputFile, "\nCould not analyze the file " + file.getAbsolutePath()); 
                    e.printStackTrace();
                }
            }
        }

        now = new Date().getTime();
        System.out.println("File analysis complete in " + (now - start) + " ms");
        FileUtils.fileAppend(outputFile, "\nFile analysis complete in " + (now - start) + " ms");
    }
    
    private static Class<AbstractCheck> getCheckClass(Rule rule) {
        for (Class<?> checkClass : CheckClasses.getCheckClasses()) {

            org.sonar.check.Rule ruleAnnotation = AnnotationUtils.getClassAnnotation(checkClass, org.sonar.check.Rule.class);
            if (ruleAnnotation.key().equals(rule.getConfigKey())) {
                return (Class<AbstractCheck>) checkClass;
            }
        }
        System.out.println("Could not find check class for config key " + rule.getConfigKey());
        return null;
    }
    
    private static AbstractCheck createCheck(Class<AbstractCheck> checkClass, Rule rule) {

        try {
            AbstractCheck check = checkClass.newInstance();
            check.setRule(rule);
            return check;
        } catch (IllegalAccessException e) {
            throw new SonarException(e);
        } catch (InstantiationException e) {
            throw new SonarException(e);
        }
    }

}
