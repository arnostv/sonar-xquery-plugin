package org.sonar.plugins.xquery.checks;

import org.sonar.plugins.xquery.AbstractSonarTest;
import org.testng.annotations.Test;

public class UnusedImportCheckTest extends AbstractSonarTest {

    private final AbstractCheck check = new UnusedImportCheck();

    @Test
    public void shouldFailOnUnusedImport() {
        checkInvalid(check, code(
                "xquery version \"1.0-ml\";",
                "import module namespace my = \"http:/module/import\";",
                "declare function test:add()",
                "{",
                " fn:true()",
                "};",
                "test:add()"
        )
        );
    }

    @Test
    public void shouldPassAndDetectUsedImport() {
        checkValid(check, code(
                "xquery version \"1.0-ml\";",
                "import module namespace my = \"http:/module/import\";",
                "declare function test:func1()",
                "{",
                " my:function()",
                "};",
                "test:func1()"
        ));
    }

}
