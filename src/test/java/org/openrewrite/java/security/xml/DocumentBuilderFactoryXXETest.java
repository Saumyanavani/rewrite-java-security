package org.openrewrite.java.security.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.security.XmlParserXXEVulnerability;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.xml.Assertions.xml;

public class DocumentBuilderFactoryXXETest implements RewriteTest{
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new XmlParserXXEVulnerability());
    }

    @Test
    void factoryIsNotVulnerable() {
        //language=java
        rewriteRun(
          java(
            """
              import javax.xml.parsers.DocumentBuilderFactory;
              import javax.xml.parsers.ParserConfigurationException; // catching unsupported features
              import javax.xml.XMLConstants;
              
              class myDBFReader {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                
                void testSetFeature(){
                    String FEATURE = null;
                    try {
                        // This is the PRIMARY defense. If DTDs (doctypes) are disallowed, almost all
                        // XML entity attacks are prevented
                        // Xerces 2 only - http://xerces.apache.org/xerces2-j/features.html#disallow-doctype-decl
                        FEATURE = "http://apache.org/xml/features/disallow-doctype-decl";
                        dbf.setFeature(FEATURE, true);
                    
                        //per Timothy Morgan's 2014 paper: "XML Schema, DTD, and Entity Attacks"
                    
                        // remaining parser logic
                    } catch (ParserConfigurationException e) {
                        // This should catch a failed setFeature feature
                        // NOTE: Each call to setFeature() should be in its own try/catch otherwise subsequent calls will be skipped.
                        // This is only important if you're ignoring errors for multi-provider support.
                        logger.info("ParserConfigurationException was thrown. The feature '" + FEATURE
                        + "' is not supported by your XML processor.");
                    }
                }
              }
              """
          )
        );
    }


}
