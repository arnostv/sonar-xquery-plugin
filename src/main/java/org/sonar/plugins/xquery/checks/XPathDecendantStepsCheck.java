/*
 * © 2013 by Intellectual Reserve, Inc. All rights reserved.
 */

package org.sonar.plugins.xquery.checks;

import org.apache.commons.lang.StringUtils;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.plugins.xquery.parser.XQueryParser;
import org.sonar.plugins.xquery.parser.XQueryTree;

/**
 * Checks for usage of text() XPath steps.
 * 
 * @since 1.0
 */
@Rule(
		key = "XpathDecendantSteps",
		name = "Avoid Using '//' in XPath",
		description = "Favor fully-qualified paths in XPath for readability and to avoid potential performance problems. For more information see http://labslcl0274.qalab.ldschurch.org:8403/viewArticle.xqy?id=424",		
		priority = Priority.MINOR)
public class XPathDecendantStepsCheck extends AbstractPathCheck {

    @Override
    public void enterExpression(XQueryTree node) {
        super.enterExpression(node);
        if (XQueryParser.PathExpr == node.getType()) {
            String expr = node.getValue();
            if (StringUtils.contains(expr, "//")) {                
                createViolations(node, "//");
            }
        }            
    }
}