/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.checks;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.BooleanUtils;
import org.sonar.check.Rule;
import org.sonar.java.RspecKey;
import org.sonar.java.model.declaration.MethodTreeImpl;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.ThrowStatementTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TypeTree;

import java.util.HashSet;
import java.util.Set;

@Rule(key = "S00112")
@RspecKey("S112")
public class RawExceptionCheck extends BaseTreeVisitor implements JavaFileScanner {

  private static final Set<String> RAW_EXCEPTIONS = ImmutableSet.of("java.lang.Throwable", "java.lang.Error", "java.lang.Exception", "java.lang.RuntimeException");

  private JavaFileScannerContext context;
  private Set<Type> exceptionsThrownByMethodInvocations = new HashSet<>();

  @Override
  public void scanFile(JavaFileScannerContext context) {
    this.context = context;
    scan(context.getTree());
  }

  @Override
  public void visitMethod(MethodTree tree) {
    super.visitMethod(tree);
    if ((tree.is(Tree.Kind.CONSTRUCTOR) || isNotOverriden(tree)) && isNotMainMethod(tree)) {
      for (TypeTree throwClause : tree.throwsClauses()) {
        Type exceptionType = throwClause.symbolType();
        if (isRawException(exceptionType) && !exceptionsThrownByMethodInvocations.contains(exceptionType)) {
          reportIssue(throwClause);
        }
      }
    }
    exceptionsThrownByMethodInvocations.clear();
  }

  @Override
  public void visitThrowStatement(ThrowStatementTree tree) {
    if (tree.expression().is(Tree.Kind.NEW_CLASS)) {
      TypeTree exception = ((NewClassTree) tree.expression()).identifier();
      if (isRawException(exception.symbolType())) {
        reportIssue(exception);
      }
    }
    super.visitThrowStatement(tree);
  }

  private void reportIssue(Tree tree) {
    context.reportIssue(this, tree, "Define and throw a dedicated exception instead of using a generic one.");
  }

  @Override
  public void visitMethodInvocation(MethodInvocationTree tree) {
    if (tree.symbol().isMethodSymbol()) {
      for (Type thrownType : ((Symbol.MethodSymbol) tree.symbol()).thrownTypes()) {
        exceptionsThrownByMethodInvocations.add(thrownType);
      }
    }
    super.visitMethodInvocation(tree);
  }

  private static boolean isRawException(Type type) {
    for (String rawException : RAW_EXCEPTIONS) {
      if (type.is(rawException)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isNotOverriden(MethodTree tree) {
    return BooleanUtils.isFalse(((MethodTreeImpl) tree).isOverriding());
  }

  private static boolean isNotMainMethod(MethodTree tree) {
    return !((MethodTreeImpl) tree).isMainMethod();
  }

}
