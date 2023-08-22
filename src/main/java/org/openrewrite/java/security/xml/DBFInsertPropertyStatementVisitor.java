package org.openrewrite.java.security.xml;

import org.openrewrite.Cursor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.Statement;

import java.util.Set;
import java.util.TreeSet;

public class DBFInsertPropertyStatementVisitor<P> extends JavaIsoVisitor<P> {

    private final J.Block scope;
    private final StringBuilder propertyTemplate = new StringBuilder();

    private final Set<String> imports = new TreeSet<>();
    private final String dbfVariableName;
    private final boolean disallowDoctypes;

    private final boolean disallowGeneralEntities;
    private final boolean disallowParameterEntities;
    private final boolean disallowLoadExternalDTD;

    public DBFInsertPropertyStatementVisitor(
            J.Block scope,
            String dbfVariableName,
            boolean accIsEmpty,
            boolean needsDisallowDoctypesTrue,
            boolean needsDisableGeneralEntities,
            boolean needsDisableParameterEntities,
            boolean needsLoadExternalDTD
    ) {

        this.scope = scope;
        this.dbfVariableName = dbfVariableName;

        if (needsDisallowDoctypesTrue && accIsEmpty) {
            disallowDoctypes = true;
            disallowGeneralEntities = false;
            disallowParameterEntities = false;
            disallowLoadExternalDTD = false;
        } else if (needsDisallowDoctypesTrue && !accIsEmpty) {
            disallowDoctypes = true;
            disallowGeneralEntities = needsDisableGeneralEntities;
            disallowParameterEntities = needsDisableParameterEntities;
            disallowLoadExternalDTD = needsLoadExternalDTD;

        } else if (!needsDisallowDoctypesTrue && !accIsEmpty) {
            disallowDoctypes = false;
            disallowGeneralEntities = false;
            disallowLoadExternalDTD = false;
            disallowParameterEntities = false;
        } else {
            disallowDoctypes = false;
            disallowGeneralEntities = false;
            disallowLoadExternalDTD = false;
            disallowParameterEntities = false;
        }
    }

    private void generateSetFeature(boolean disallowDoctypes) {
        if (disallowDoctypes && !disallowGeneralEntities && !disallowParameterEntities && !disallowLoadExternalDTD) {
            imports.add("javax.xml.parsers.ParserConfigurationException");
            propertyTemplate.append(
                    "String FEATURE = \"http://apache.org/xml/features/disallow-doctype-decl\";\n" +
                    "try {\n" +
                    "   " + dbfVariableName + ".setFeature(FEATURE, true);\n" +
                    "} catch (ParserConfigurationException e) {\n" +
                    "    throw new IllegalStateException(\"ParserConfigurationException was thrown. The feature '\"\n" +
                    "            + FEATURE + \"' is not supported by your XML processor.\", e);\n" +
                    "}\n"
            );
        } else if (disallowDoctypes && disallowGeneralEntities && disallowParameterEntities && disallowLoadExternalDTD) {
            imports.add("javax.xml.parsers.ParserConfigurationException");
            propertyTemplate.append(
                    "String FEATURE = null;\n" +
                    "try {\n" +
                    "   FEATURE = \"http://xml.org/sax/features/external-parameter-entities\";\n" +
                    "   " + dbfVariableName + ".setFeature(FEATURE, false);\n" +
                    "\n" +
                    "   FEATURE = \"http://apache.org/xml/features/nonvalidating/load-external-dtd\";\n" +
                    "   " + dbfVariableName + ".setFeature(FEATURE, false);\n" +
                    "\n" +
                    "   FEATURE = \"http://xml.org/sax/features/external-general-entities\";\n" +
                    "   " + dbfVariableName + ".setFeature(FEATURE, false);\n" +
                    "\n" +
                    "   " + dbfVariableName + ".setXIncludeAware(false);\n" +
                    "   " + dbfVariableName + ".setExpandEntityReferences(false);\n" +
                    "\n" +
                    "   " + dbfVariableName + ".setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);\n" +
                    "} catch (ParserConfigurationException e) {\n" +
                    "    throw new IllegalStateException(\"ParserConfigurationException was thrown. The feature '\"\n" +
                    "            + FEATURE + \"' is not supported by your XML processor.\", e);\n" +
                    "}\n"
            );

        }
    }

    @Override
    public J.Block visitBlock(J.Block block, P ctx) {
        J.Block b = super.visitBlock(block, ctx);
        Statement beforeStatement = null;
        if (b.isScope(scope)) {
            for (int i = b.getStatements().size() - 2; i > -1; i--) {
                Statement st = b.getStatements().get(i);
                Statement stBefore = b.getStatements().get(i + 1);
                if (st instanceof J.MethodInvocation) {
                    J.MethodInvocation m = (J.MethodInvocation) st;
                    if (DocumentBuilderFactoryFixVisitor.DBF_NEW_INSTANCE.matches(m) || DocumentBuilderFactoryFixVisitor.DBF_PARSER_SET_FEATURE.matches(m)) {
                        beforeStatement = stBefore;
                    }
                } else if (st instanceof J.VariableDeclarations) {
                    J.VariableDeclarations vd = (J.VariableDeclarations) st;
                    if (vd.getVariables().get(0).getInitializer() instanceof J.MethodInvocation) {
                        J.MethodInvocation m = (J.MethodInvocation) vd.getVariables().get(0).getInitializer();
                        if (m != null && DocumentBuilderFactoryFixVisitor.DBF_NEW_INSTANCE.matches(m)) {
                            beforeStatement = stBefore;
                        }
                    }
                }
            }

            generateSetFeature(disallowDoctypes);

            if (getCursor().getParent() != null && getCursor().getParent().getValue() instanceof J.ClassDeclaration) {
                propertyTemplate.insert(0, "{\n").append("}");
            }
            JavaCoordinates insertCoordinates = beforeStatement != null ?
                    beforeStatement.getCoordinates().before() :
                    b.getCoordinates().lastStatement();
            System.out.println(propertyTemplate.toString());
            b = JavaTemplate
                    .builder(propertyTemplate.toString())
                    .imports(imports.toArray(new String[0]))
                    .contextSensitive()
                    .build()
                    .apply(new Cursor(getCursor().getParent(), b), insertCoordinates);
            imports.forEach(this::maybeAddImport);
        }
        return b;
    }
}
