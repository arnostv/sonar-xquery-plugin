package org.sonar.plugins.xquery.checks;

import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.xquery.parser.XQueryParser;
import org.sonar.plugins.xquery.parser.XQueryTree;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Rule(
        key = "UnusedImport",
        name = "Unused import",
        description = "Namespace prefix is declared but never used",
        priority = Priority.MAJOR
)
public class UnusedImportCheck extends AbstractCheck {

    private Map<String, String> foundNamespaces = new HashMap<String, String>();
    private Map<String, XQueryTree> namespaceNode = new HashMap<String, XQueryTree>();
    private Set<String> unusedPrefix = new HashSet<String>();


    @Override
    public void enterSource(XQueryTree node) {
        super.enterSource(node);
        System.out.println("entered source " + node);
        foundNamespaces.clear();
        namespaceNode.clear();
        unusedPrefix.clear();
    }


    @Override
    public void enterExpression(XQueryTree node) {
        super.enterExpression(node);
        String nodeAsString = node.toString();
        System.out.println("entered expression " + nodeAsString);

        if (node.getType() == XQueryParser.ModuleDecl) {
//            System.out.println("Module decl " + node);
        } else if (node.getType() == XQueryParser.NamespaceDecl) {
//            System.out.println("Namespace decl " + node);
        } else if (node.getType() == XQueryParser.NamespaceDecls) {
//            System.out.println("Namespace decls " + node);
        } else if (node.getType() == XQueryParser.Imports) {
//            System.out.println("Imports " + node);
        } else if (node.getType() == XQueryParser.QName) {
            System.out.println("QName " + node);
            processQName(node);
        } else if (node.getType() == XQueryParser.ModuleImport) {
//            System.out.println("ModuleImport " + node);
            processModuleImport(node);
        }
    }

    private void processQName(XQueryTree node) {
        if (node.getChildCount() == 3 && ":".equals(node.getChild(1).getText())) {
            final String prefix = node.getChild(0).getText();
            System.out.println("*** node for " + prefix);
            unusedPrefix.remove(prefix);
        }


    }

    private void processModuleImport(XQueryTree node) {
        final XQueryTree modulePrefixNode = (XQueryTree) node.getFirstChildWithType(XQueryParser.ModulePrefix);
        final XQueryTree moduleNamespace = (XQueryTree) node.getFirstChildWithType(XQueryParser.ModuleNamespace);

        if (modulePrefixNode != null && moduleNamespace != null && moduleNamespace.getChildCount() > 0) {
            final String modulePrefix = modulePrefixNode.getTextValue();
            final String namespace = moduleNamespace.getChild(0).getTextValue();

            foundNamespaces.put(modulePrefix, namespace);
            namespaceNode.put(modulePrefix, node);
            unusedPrefix.add(modulePrefix);
        }


        System.out.println(modulePrefixNode + " " + moduleNamespace);
    }

    @Override
    public void exitSource(XQueryTree node) {
        super.exitSource(node);

        for (String prefix : unusedPrefix) {
            createViolation(namespaceNode.get(prefix).getLine());
        }
    }

}
