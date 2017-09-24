/*
 * Copyright (c) 2013, 2016 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.truffleruby.parser;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.joni.NameEntry;
import org.joni.Regex;
import org.joni.Syntax;
import org.truffleruby.Log;
import org.truffleruby.RubyContext;
import org.truffleruby.RubyLanguage;
import org.truffleruby.builtins.PrimitiveNodeConstructor;
import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.IsNilNode;
import org.truffleruby.core.IsRubiniusUndefinedNode;
import org.truffleruby.core.RaiseIfFrozenNode;
import org.truffleruby.core.array.ArrayAppendOneNodeGen;
import org.truffleruby.core.array.ArrayConcatNode;
import org.truffleruby.core.array.ArrayDropTailNode;
import org.truffleruby.core.array.ArrayDropTailNodeGen;
import org.truffleruby.core.array.ArrayGetTailNodeGen;
import org.truffleruby.core.array.ArrayLiteralNode;
import org.truffleruby.core.array.ArrayToObjectArrayNodeGen;
import org.truffleruby.core.array.PrimitiveArrayNodeFactory;
import org.truffleruby.core.cast.HashCastNodeGen;
import org.truffleruby.core.cast.SplatCastNode;
import org.truffleruby.core.cast.SplatCastNodeGen;
import org.truffleruby.core.cast.StringToSymbolNodeGen;
import org.truffleruby.core.cast.ToProcNodeGen;
import org.truffleruby.core.cast.ToSNode;
import org.truffleruby.core.cast.ToSNodeGen;
import org.truffleruby.core.hash.ConcatHashLiteralNode;
import org.truffleruby.core.hash.EnsureSymbolKeysNode;
import org.truffleruby.core.hash.HashLiteralNode;
import org.truffleruby.core.kernel.KernelNodesFactory;
import org.truffleruby.core.module.ModuleNodesFactory;
import org.truffleruby.core.numeric.BignumOperations;
import org.truffleruby.core.proc.ProcType;
import org.truffleruby.core.range.RangeNodesFactory;
import org.truffleruby.core.regexp.InterpolatedRegexpNode;
import org.truffleruby.core.regexp.RegexpNodes;
import org.truffleruby.core.regexp.RegexpOptions;
import org.truffleruby.core.rope.CodeRange;
import org.truffleruby.core.rope.Rope;
import org.truffleruby.core.rope.RopeConstants;
import org.truffleruby.core.string.InterpolatedStringNode;
import org.truffleruby.core.string.StringOperations;
import org.truffleruby.core.string.StringUtils;
import org.truffleruby.language.LexicalScope;
import org.truffleruby.language.NotProvided;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.RubyRootNode;
import org.truffleruby.language.SourceIndexLength;
import org.truffleruby.language.Visibility;
import org.truffleruby.language.arguments.ArrayIsAtLeastAsLargeAsNode;
import org.truffleruby.language.arguments.SingleBlockArgNode;
import org.truffleruby.language.constants.ReadConstantNode;
import org.truffleruby.language.constants.ReadConstantWithDynamicScopeNode;
import org.truffleruby.language.constants.ReadConstantWithLexicalScopeNode;
import org.truffleruby.language.constants.WriteConstantNode;
import org.truffleruby.language.control.AndNode;
import org.truffleruby.language.control.BreakID;
import org.truffleruby.language.control.BreakNode;
import org.truffleruby.language.control.ElidableResultNode;
import org.truffleruby.language.control.FrameOnStackNode;
import org.truffleruby.language.control.IfElseNode;
import org.truffleruby.language.control.IfNode;
import org.truffleruby.language.control.NextNode;
import org.truffleruby.language.control.NotNode;
import org.truffleruby.language.control.OnceNode;
import org.truffleruby.language.control.OrNode;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.control.RedoNode;
import org.truffleruby.language.control.RetryNode;
import org.truffleruby.language.control.ReturnID;
import org.truffleruby.language.control.ReturnNode;
import org.truffleruby.language.control.UnlessNode;
import org.truffleruby.language.control.WhileNode;
import org.truffleruby.language.defined.DefinedNode;
import org.truffleruby.language.defined.DefinedWrapperNode;
import org.truffleruby.language.dispatch.RubyCallNode;
import org.truffleruby.language.dispatch.RubyCallNodeParameters;
import org.truffleruby.language.exceptions.DisablingBacktracesNode;
import org.truffleruby.language.exceptions.EnsureNode;
import org.truffleruby.language.exceptions.RescueAnyNode;
import org.truffleruby.language.exceptions.RescueClassesNode;
import org.truffleruby.language.exceptions.RescueNode;
import org.truffleruby.language.exceptions.RescueSplatNode;
import org.truffleruby.language.exceptions.TryNode;
import org.truffleruby.language.globals.AliasGlobalVarNode;
import org.truffleruby.language.globals.CheckMatchVariableTypeNode;
import org.truffleruby.language.globals.CheckOutputSeparatorVariableTypeNode;
import org.truffleruby.language.globals.CheckProgramNameVariableTypeNode;
import org.truffleruby.language.globals.CheckRecordSeparatorVariableTypeNode;
import org.truffleruby.language.globals.CheckStdoutVariableTypeNode;
import org.truffleruby.language.globals.ReadGlobalVariableNodeGen;
import org.truffleruby.language.globals.ReadLastBacktraceNode;
import org.truffleruby.language.globals.ReadMatchReferenceNode;
import org.truffleruby.language.globals.ReadThreadLocalGlobalVariableNode;
import org.truffleruby.language.globals.UpdateLastBacktraceNode;
import org.truffleruby.language.globals.UpdateVerbosityNode;
import org.truffleruby.language.globals.WriteGlobalVariableNodeGen;
import org.truffleruby.language.globals.WriteReadOnlyGlobalNode;
import org.truffleruby.language.globals.CheckSafeLevelNode;
import org.truffleruby.language.literal.BooleanLiteralNode;
import org.truffleruby.language.literal.FloatLiteralNode;
import org.truffleruby.language.literal.IntegerFixnumLiteralNode;
import org.truffleruby.language.literal.LongFixnumLiteralNode;
import org.truffleruby.language.literal.NilLiteralNode;
import org.truffleruby.language.literal.ObjectLiteralNode;
import org.truffleruby.language.literal.StringLiteralNode;
import org.truffleruby.language.locals.DeclarationFlipFlopStateNode;
import org.truffleruby.language.locals.FlipFlopNode;
import org.truffleruby.language.locals.FlipFlopStateNode;
import org.truffleruby.language.locals.InitFlipFlopSlotNode;
import org.truffleruby.language.locals.LocalFlipFlopStateNode;
import org.truffleruby.language.locals.LocalVariableType;
import org.truffleruby.language.locals.ReadLocalVariableNode;
import org.truffleruby.language.methods.AddMethodNodeGen;
import org.truffleruby.language.methods.Arity;
import org.truffleruby.language.methods.BlockDefinitionNode;
import org.truffleruby.language.methods.CatchBreakNode;
import org.truffleruby.language.methods.ExceptionTranslatingNode;
import org.truffleruby.language.methods.GetCurrentVisibilityNode;
import org.truffleruby.language.methods.GetDefaultDefineeNode;
import org.truffleruby.language.methods.MethodDefinitionNode;
import org.truffleruby.language.methods.ModuleBodyDefinitionNode;
import org.truffleruby.language.methods.SharedMethodInfo;
import org.truffleruby.language.methods.UnsupportedOperationBehavior;
import org.truffleruby.language.objects.DefineClassNode;
import org.truffleruby.language.objects.DefineModuleNode;
import org.truffleruby.language.objects.DefineModuleNodeGen;
import org.truffleruby.language.objects.DynamicLexicalScopeNode;
import org.truffleruby.language.objects.GetDynamicLexicalScopeNode;
import org.truffleruby.language.objects.LexicalScopeNode;
import org.truffleruby.language.objects.ReadClassVariableNode;
import org.truffleruby.language.objects.ReadInstanceVariableNode;
import org.truffleruby.language.objects.RunModuleDefinitionNode;
import org.truffleruby.language.objects.SelfNode;
import org.truffleruby.language.objects.SingletonClassNode;
import org.truffleruby.language.objects.SingletonClassNodeGen;
import org.truffleruby.language.objects.WriteClassVariableNode;
import org.truffleruby.language.objects.WriteInstanceVariableNode;
import org.truffleruby.language.threadlocal.GetFromThreadAndFrameLocalStorageNode;
import org.truffleruby.language.threadlocal.GetThreadLocalsObjectNode;
import org.truffleruby.language.threadlocal.GetThreadLocalsObjectNodeGen;
import org.truffleruby.language.threadlocal.SetInThreadAndFrameLocalStorageNode;
import org.truffleruby.language.yield.YieldExpressionNode;
import org.truffleruby.parser.ast.AliasParseNode;
import org.truffleruby.parser.ast.AndParseNode;
import org.truffleruby.parser.ast.ArgsCatParseNode;
import org.truffleruby.parser.ast.ArgsParseNode;
import org.truffleruby.parser.ast.ArgsPushParseNode;
import org.truffleruby.parser.ast.ArgumentParseNode;
import org.truffleruby.parser.ast.ArrayParseNode;
import org.truffleruby.parser.ast.AssignableParseNode;
import org.truffleruby.parser.ast.AttrAssignParseNode;
import org.truffleruby.parser.ast.BackRefParseNode;
import org.truffleruby.parser.ast.BeginParseNode;
import org.truffleruby.parser.ast.BigRationalParseNode;
import org.truffleruby.parser.ast.BignumParseNode;
import org.truffleruby.parser.ast.BlockParseNode;
import org.truffleruby.parser.ast.BlockPassParseNode;
import org.truffleruby.parser.ast.BreakParseNode;
import org.truffleruby.parser.ast.CallParseNode;
import org.truffleruby.parser.ast.CaseParseNode;
import org.truffleruby.parser.ast.ClassParseNode;
import org.truffleruby.parser.ast.ClassVarAsgnParseNode;
import org.truffleruby.parser.ast.ClassVarParseNode;
import org.truffleruby.parser.ast.Colon2ConstParseNode;
import org.truffleruby.parser.ast.Colon2ImplicitParseNode;
import org.truffleruby.parser.ast.Colon2ParseNode;
import org.truffleruby.parser.ast.Colon3ParseNode;
import org.truffleruby.parser.ast.ComplexParseNode;
import org.truffleruby.parser.ast.ConstDeclParseNode;
import org.truffleruby.parser.ast.ConstParseNode;
import org.truffleruby.parser.ast.DAsgnParseNode;
import org.truffleruby.parser.ast.DRegexpParseNode;
import org.truffleruby.parser.ast.DStrParseNode;
import org.truffleruby.parser.ast.DSymbolParseNode;
import org.truffleruby.parser.ast.DVarParseNode;
import org.truffleruby.parser.ast.DXStrParseNode;
import org.truffleruby.parser.ast.DefinedParseNode;
import org.truffleruby.parser.ast.DefnParseNode;
import org.truffleruby.parser.ast.DefsParseNode;
import org.truffleruby.parser.ast.DotParseNode;
import org.truffleruby.parser.ast.EncodingParseNode;
import org.truffleruby.parser.ast.EnsureParseNode;
import org.truffleruby.parser.ast.EvStrParseNode;
import org.truffleruby.parser.ast.FCallParseNode;
import org.truffleruby.parser.ast.FalseParseNode;
import org.truffleruby.parser.ast.FixnumParseNode;
import org.truffleruby.parser.ast.FlipParseNode;
import org.truffleruby.parser.ast.FloatParseNode;
import org.truffleruby.parser.ast.ForParseNode;
import org.truffleruby.parser.ast.GlobalAsgnParseNode;
import org.truffleruby.parser.ast.GlobalVarParseNode;
import org.truffleruby.parser.ast.HashParseNode;
import org.truffleruby.parser.ast.IfParseNode;
import org.truffleruby.parser.ast.InstAsgnParseNode;
import org.truffleruby.parser.ast.InstVarParseNode;
import org.truffleruby.parser.ast.IterParseNode;
import org.truffleruby.parser.ast.LambdaParseNode;
import org.truffleruby.parser.ast.ListParseNode;
import org.truffleruby.parser.ast.LiteralParseNode;
import org.truffleruby.parser.ast.LocalAsgnParseNode;
import org.truffleruby.parser.ast.LocalVarParseNode;
import org.truffleruby.parser.ast.Match2ParseNode;
import org.truffleruby.parser.ast.Match3ParseNode;
import org.truffleruby.parser.ast.MatchParseNode;
import org.truffleruby.parser.ast.MethodDefParseNode;
import org.truffleruby.parser.ast.ModuleParseNode;
import org.truffleruby.parser.ast.MultipleAsgnParseNode;
import org.truffleruby.parser.ast.NextParseNode;
import org.truffleruby.parser.ast.NilImplicitParseNode;
import org.truffleruby.parser.ast.NilParseNode;
import org.truffleruby.parser.ast.NthRefParseNode;
import org.truffleruby.parser.ast.OpAsgnAndParseNode;
import org.truffleruby.parser.ast.OpAsgnConstDeclParseNode;
import org.truffleruby.parser.ast.OpAsgnOrParseNode;
import org.truffleruby.parser.ast.OpAsgnParseNode;
import org.truffleruby.parser.ast.OpElementAsgnParseNode;
import org.truffleruby.parser.ast.OrParseNode;
import org.truffleruby.parser.ast.ParseNode;
import org.truffleruby.parser.ast.PostExeParseNode;
import org.truffleruby.parser.ast.PreExeParseNode;
import org.truffleruby.parser.ast.RationalParseNode;
import org.truffleruby.parser.ast.RedoParseNode;
import org.truffleruby.parser.ast.RegexpParseNode;
import org.truffleruby.parser.ast.RescueBodyParseNode;
import org.truffleruby.parser.ast.RescueParseNode;
import org.truffleruby.parser.ast.RetryParseNode;
import org.truffleruby.parser.ast.ReturnParseNode;
import org.truffleruby.parser.ast.SClassParseNode;
import org.truffleruby.parser.ast.SValueParseNode;
import org.truffleruby.parser.ast.SelfParseNode;
import org.truffleruby.parser.ast.SideEffectFree;
import org.truffleruby.parser.ast.SplatParseNode;
import org.truffleruby.parser.ast.StarParseNode;
import org.truffleruby.parser.ast.StrParseNode;
import org.truffleruby.parser.ast.SymbolParseNode;
import org.truffleruby.parser.ast.TrueParseNode;
import org.truffleruby.parser.ast.TruffleFragmentParseNode;
import org.truffleruby.parser.ast.UndefParseNode;
import org.truffleruby.parser.ast.UntilParseNode;
import org.truffleruby.parser.ast.VAliasParseNode;
import org.truffleruby.parser.ast.VCallParseNode;
import org.truffleruby.parser.ast.WhenParseNode;
import org.truffleruby.parser.ast.WhileParseNode;
import org.truffleruby.parser.ast.XStrParseNode;
import org.truffleruby.parser.ast.YieldParseNode;
import org.truffleruby.parser.ast.ZArrayParseNode;
import org.truffleruby.parser.ast.visitor.NodeVisitor;
import org.truffleruby.parser.parser.ParseNodeTuple;
import org.truffleruby.parser.parser.ParserSupport;
import org.truffleruby.parser.scope.StaticScope;
import org.truffleruby.platform.graal.AssertConstantNodeGen;
import org.truffleruby.platform.graal.AssertNotCompiledNodeGen;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * A JRuby parser node visitor which translates JRuby AST nodes into truffle Nodes. Therefore there is some namespace
 * contention here! We make all references to JRuby explicit.
 */
public class BodyTranslator extends Translator {

    protected final BodyTranslator parent;
    protected final TranslatorEnvironment environment;

    public boolean translatingForStatement = false;
    private boolean translatingNextExpression = false;
    private boolean translatingWhile = false;
    protected String currentCallMethodName = null;

    private boolean privately = false;

    public BodyTranslator(com.oracle.truffle.api.nodes.Node currentNode, RubyContext context, BodyTranslator parent, TranslatorEnvironment environment, Source source, ParserContext parserContext, boolean topLevel) {
        super(currentNode, context, source, parserContext);
        parserSupport = new ParserSupport(context, source.getName());
        this.parent = parent;
        this.environment = environment;
    }

    private RubyNode translateNameNodeToSymbol(ParseNode node) {
        if (node instanceof LiteralParseNode) {
            return new ObjectLiteralNode(context.getSymbolTable().getSymbol(((LiteralParseNode) node).getName()));
        } else if (node instanceof SymbolParseNode) {
            return node.accept(this);
        } else {
            throw new UnsupportedOperationException(node.getClass().getName());
        }
    }

    @Override
    public RubyNode visitAliasNode(AliasParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode oldNameNode = translateNameNodeToSymbol(node.getOldName());
        final RubyNode newNameNode = translateNameNodeToSymbol(node.getNewName());

        final RubyNode ret = ModuleNodesFactory.AliasMethodNodeFactory.create(
                new RaiseIfFrozenNode(new GetDefaultDefineeNode()),
                newNameNode,
                oldNameNode);

        ret.unsafeSetSourceSection(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitVAliasNode(VAliasParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret = new AliasGlobalVarNode(node.getOldName(), node.getNewName());

        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitAndNode(AndParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode x = translateNodeOrNil(sourceSection, node.getFirstNode());
        final RubyNode y = translateNodeOrNil(sourceSection, node.getSecondNode());

        final RubyNode ret = new AndNode(x, y);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitArgsCatNode(ArgsCatParseNode node) {
        final List<ParseNode> nodes = new ArrayList<>();
        collectArgsCatNodes(nodes, node);

        final List<RubyNode> translatedNodes = new ArrayList<>();

        for (ParseNode catNode : nodes) {
            translatedNodes.add(catNode.accept(this));
        }

        final RubyNode ret = new ArrayConcatNode(translatedNodes.toArray(new RubyNode[translatedNodes.size()]));
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    // ArgsCatNodes can be nested - this collects them into a flat list of children
    private void collectArgsCatNodes(List<ParseNode> nodes, ArgsCatParseNode node) {
        if (node.getFirstNode() instanceof ArgsCatParseNode) {
            collectArgsCatNodes(nodes, (ArgsCatParseNode) node.getFirstNode());
        } else {
            nodes.add(node.getFirstNode());
        }

        if (node.getSecondNode() instanceof ArgsCatParseNode) {
            collectArgsCatNodes(nodes, (ArgsCatParseNode) node.getSecondNode());
        } else {
            // ArgsCatParseNode implicitly splat its second argument. See Helpers.argsCat.
            ParseNode secondNode = new SplatParseNode(node.getSecondNode().getPosition(), node.getSecondNode());
            nodes.add(secondNode);
        }
    }

    @Override
    public RubyNode visitArgsPushNode(ArgsPushParseNode node) {
        final RubyNode args = node.getFirstNode().accept(this);
        final RubyNode value = node.getSecondNode().accept(this);
        final RubyNode ret = ArrayAppendOneNodeGen.create(
                KernelNodesFactory.DupNodeFactory.create(new RubyNode[] { args }),
                value);

        ret.unsafeSetSourceSection(node.getPosition());

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitArrayNode(ArrayParseNode node) {
        final ParseNode[] values = node.children();

        final RubyNode[] translatedValues = new RubyNode[values.length];

        for (int n = 0; n < values.length; n++) {
            translatedValues[n] = values[n].accept(this);
        }

        final RubyNode ret = ArrayLiteralNode.create(translatedValues);
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitAttrAssignNode(AttrAssignParseNode node) {
        final CallParseNode callNode = new CallParseNode(
                node.getPosition(), node.getReceiverNode(), node.getName(), node.getArgsNode(), null, node.isLazy());

        copyNewline(node, callNode);
        boolean isAccessorOnSelf = (node.getReceiverNode() instanceof SelfParseNode);
        final RubyNode actualCall = translateCallNode(callNode, isAccessorOnSelf, false, true);

        return addNewlineIfNeeded(node, actualCall);
    }

    @Override
    public RubyNode visitBeginNode(BeginParseNode node) {
        final RubyNode ret = node.getBodyNode().accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitBignumNode(BignumParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        // These aren't always Bignums!

        final BigInteger value = node.getValue();
        final RubyNode ret = bignumOrFixnumNode(value);
        ret.unsafeSetSourceSection(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode bignumOrFixnumNode(BigInteger value) {
        if (value.bitLength() >= 64) {
            return new ObjectLiteralNode(BignumOperations.createBignum(context, value));
        } else {
            return new LongFixnumLiteralNode(value.longValue());
        }
    }

    @Override
    public RubyNode visitBlockNode(BlockParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final List<RubyNode> translatedChildren = new ArrayList<>();

        final int start = node.getPosition().getCharIndex();
        int end = node.getPosition().getCharEnd();

        for (ParseNode child : node.children()) {
            if (child.getPosition() != null) {
                end = Math.max(end, child.getPosition().getCharEnd());
            }

            final RubyNode translatedChild = translateNodeOrNil(sourceSection, child);

            if (!(translatedChild instanceof DeadNode)) {
                translatedChildren.add(translatedChild);
            }
        }

        final RubyNode ret;

        if (translatedChildren.size() == 1) {
            ret = translatedChildren.get(0);
        } else {
            ret = sequence(new SourceIndexLength(start, end - start), translatedChildren);
        }

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitBreakNode(BreakParseNode node) {
        assert environment.isBlock() || translatingWhile : "The parser did not see an invalid break";
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode resultNode = translateNodeOrNil(sourceSection, node.getValueNode());

        final RubyNode ret = new BreakNode(environment.getBreakID(), translatingWhile, resultNode);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitCallNode(CallParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final ParseNode receiver = node.getReceiverNode();
        final String methodName = node.getName();

        if (receiver instanceof StrParseNode && methodName.equals("freeze")) {
            final StrParseNode strNode = (StrParseNode) receiver;
            final Rope byteList = strNode.getValue();
            final CodeRange codeRange = strNode.getCodeRange();

            final Rope rope = context.getRopeTable().getRope(byteList, codeRange);

            final DynamicObject frozenString = context.getFrozenStrings().getFrozenString(rope);

            return addNewlineIfNeeded(node, Translator.withSourceSection(sourceSection, new DefinedWrapperNode(context.getCoreStrings().METHOD, new ObjectLiteralNode(frozenString))));
        }

        if (receiver instanceof ConstParseNode
                && ((ConstParseNode) receiver).getName().equals("Truffle")) {
            // Truffle.<method>

            switch (methodName) {
                case "primitive":
                    throw new AssertionError("Invalid usage of Truffle.primitive at " + RubyLanguage.fileLine(sourceSection.toSourceSection(source)));
                case "invoke_primitive": {
                    final RubyNode ret = translateRubiniusInvokePrimitive(sourceSection, node);
                    return addNewlineIfNeeded(node, ret);
                }
                case "privately": {
                    final RubyNode ret = translateRubiniusPrivately(node);
                    return addNewlineIfNeeded(node, ret);
                }
                case "single_block_arg": {
                    final RubyNode ret = translateSingleBlockArg(sourceSection, node);
                    return addNewlineIfNeeded(node, ret);
                }
                case "check_frozen": {
                    final RubyNode ret = translateCheckFrozen(sourceSection);
                    return addNewlineIfNeeded(node, ret);
                }
            }
        } else if (receiver instanceof Colon2ConstParseNode // Truffle::Graal.<method>
                && ((Colon2ConstParseNode) receiver).getLeftNode() instanceof ConstParseNode
                && ((ConstParseNode) ((Colon2ConstParseNode) receiver).getLeftNode()).getName().equals("Truffle")
                && ((Colon2ConstParseNode) receiver).getName().equals("Graal")) {
            if (methodName.equals("assert_constant")) {
                final RubyNode ret = AssertConstantNodeGen.create(((ArrayParseNode) node.getArgsNode()).get(0).accept(this));
                ret.unsafeSetSourceSection(sourceSection);
                return addNewlineIfNeeded(node, ret);
            } else if (methodName.equals("assert_not_compiled")) {
                final RubyNode ret = AssertNotCompiledNodeGen.create();
                ret.unsafeSetSourceSection(sourceSection);
                return addNewlineIfNeeded(node, ret);
            }
        } else if (receiver instanceof VCallParseNode // undefined.equal?(obj)
                && ((VCallParseNode) receiver).getName().equals("undefined")
                && getSourcePath(sourceSection).startsWith(corePath())
                && methodName.equals("equal?")) {
            RubyNode argument = translateArgumentsAndBlock(sourceSection, null, node.getArgsNode(), methodName).getArguments()[0];
            final RubyNode ret = new IsRubiniusUndefinedNode(argument);
            ret.unsafeSetSourceSection(sourceSection);
            return addNewlineIfNeeded(node, ret);
        }

        return translateCallNode(node, false, false, false);
    }

    protected RubyNode translateRubiniusPrimitive(SourceIndexLength sourceSection, BlockParseNode body, RubyNode loadArguments) {
        /*
         * Translates something that looks like
         *
         *   def foo
         *     Truffle.primitive :foo
         *     fallback code
         *   end
         *
         * into
         *
         *   if value = CallPrimitiveNode(FooNode(arg1, arg2, ..., argN))
         *     return value
         *   else
         *     fallback code
         *   end
         *
         * Where the arguments are the same arguments as the method. It looks like this is only exercised with simple
         * arguments so we're not worrying too much about what happens when they're more complicated (rest,
         * keywords etc).
         */

        final CallParseNode node = (CallParseNode) body.get(0);
        final ArrayParseNode argsNode = (ArrayParseNode) node.getArgsNode();
        if (argsNode.size() != 1 || !(argsNode.get(0) instanceof SymbolParseNode)) {
            throw new UnsupportedOperationException("Truffle.primitive must have a single literal symbol argument");
        }

        final String primitiveName = ((SymbolParseNode) argsNode.get(0)).getName();

        BlockParseNode fallback = new BlockParseNode(body.getPosition());
        for (int i = 1; i < body.size(); i++) {
            fallback.add(body.get(i));
        }
        RubyNode fallbackNode = fallback.accept(this);
        fallbackNode = sequence(sourceSection, Arrays.asList(loadArguments, fallbackNode));

        final PrimitiveNodeConstructor primitive = context.getPrimitiveManager().getPrimitive(primitiveName);
        return primitive.createCallPrimitiveNode(context, source, sourceSection, fallbackNode);
    }

    private RubyNode translateRubiniusInvokePrimitive(SourceIndexLength sourceSection, CallParseNode node) {
        /*
         * Translates something that looks like
         *
         *   Truffle.invoke_primitive :foo, arg1, arg2, argN
         *
         * into
         *
         *   InvokePrimitiveNode(FooNode(arg1, arg2, ..., argN))
         */

        final ArrayParseNode args = (ArrayParseNode) node.getArgsNode();

        if (args.size() < 1 || !(args.get(0) instanceof SymbolParseNode)) {
            throw new UnsupportedOperationException("Truffle.invoke_primitive must have at least an initial literal symbol argument");
        }

        final String primitiveName = ((SymbolParseNode) args.get(0)).getName();

        final PrimitiveNodeConstructor primitive = context.getPrimitiveManager().getPrimitive(primitiveName);

        final List<RubyNode> arguments = new ArrayList<>();

        // The first argument was the symbol so we ignore it
        for (int n = 1; n < args.size(); n++) {
            RubyNode readArgumentNode = args.get(n).accept(this);
            arguments.add(readArgumentNode);
        }

        return primitive.createInvokePrimitiveNode(context, source, sourceSection, arguments.toArray(new RubyNode[arguments.size()]));
    }

    private RubyNode translateRubiniusPrivately(CallParseNode node) {
        /*
         * Translates something that looks like
         *
         *   Truffle.privately { foo }
         *
         * into just
         *
         *   foo
         *
         * While we translate foo we'll mark all call sites as ignoring visbility.
         */

        if (!(node.getIterNode() instanceof IterParseNode)) {
            throw new UnsupportedOperationException("Truffle.privately needs a literal block");
        }

        final ArrayParseNode argsNode = (ArrayParseNode) node.getArgsNode();
        if (argsNode != null && argsNode.size() > 0) {
            throw new UnsupportedOperationException("Truffle.privately should not have any arguments");
        }

        /*
         * Normally when you visit an 'iter' (block) node it will set the method name for you, so that we can name the
         * block something like 'times-block'. Here we bypass the iter node and translate its child. So we set the
         * name here.
         */

        currentCallMethodName = "privately";

        /*
         * While we translate the body of the iter we want to create all call nodes with the ignore-visbility flag.
         * This flag is checked in visitCallNode.
         */

        final boolean previousPrivately = privately;
        privately = true;

        try {
            return (((IterParseNode) node.getIterNode()).getBodyNode()).accept(this);
        } finally {
            // Restore the previous value of the privately flag - allowing for nesting

            privately = previousPrivately;
        }
    }

    public RubyNode translateSingleBlockArg(SourceIndexLength sourceSection, CallParseNode node) {
        final RubyNode ret = new SingleBlockArgNode();
        ret.unsafeSetSourceSection(sourceSection);
        return ret;
    }

    private RubyNode translateCheckFrozen(SourceIndexLength sourceSection) {
        return Translator.withSourceSection(sourceSection, new RaiseIfFrozenNode(new SelfNode(environment.getFrameDescriptor())));
    }

    private RubyNode translateCallNode(CallParseNode node, boolean ignoreVisibility, boolean isVCall, boolean isAttrAssign) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode receiver = node.getReceiverNode().accept(this);

        ParseNode args = node.getArgsNode();
        ParseNode block = node.getIterNode();

        if (block == null && args instanceof IterParseNode) {
            block = args;
            args = null;
        }

        final String methodName = node.getName();
        final ArgumentsAndBlockTranslation argumentsAndBlock = translateArgumentsAndBlock(sourceSection, block, args, methodName);

        final List<RubyNode> children = new ArrayList<>();

        if (argumentsAndBlock.getBlock() != null) {
            children.add(argumentsAndBlock.getBlock());
        }

        children.addAll(Arrays.asList(argumentsAndBlock.getArguments()));

        final SourceIndexLength enclosingSourceSection = enclosing(sourceSection, children.toArray(new RubyNode[children.size()]));

        RubyCallNodeParameters callParameters = new RubyCallNodeParameters(receiver, methodName, argumentsAndBlock.getBlock(), argumentsAndBlock.getArguments(), argumentsAndBlock.isSplatted(), privately || ignoreVisibility, isVCall, node.isLazy(), isAttrAssign);
        RubyNode translated = Translator.withSourceSection(enclosingSourceSection, context.getCoreMethods().createCallNode(callParameters));

        translated = wrapCallWithLiteralBlock(argumentsAndBlock, translated);

        return addNewlineIfNeeded(node, translated);
    }

    protected RubyNode wrapCallWithLiteralBlock(ArgumentsAndBlockTranslation argumentsAndBlock, RubyNode callNode) {
        if (argumentsAndBlock.getBlock() instanceof BlockDefinitionNode) { // if we have a literal block, break breaks out of this call site
            callNode = new FrameOnStackNode(callNode, argumentsAndBlock.getFrameOnStackMarkerSlot());
            final BlockDefinitionNode blockDef = (BlockDefinitionNode) argumentsAndBlock.getBlock();
            return new CatchBreakNode(blockDef.getBreakID(), callNode);
        } else {
            return callNode;
        }
    }

    protected static class ArgumentsAndBlockTranslation {

        private final RubyNode block;
        private final RubyNode[] arguments;
        private final boolean isSplatted;
        private final FrameSlot frameOnStackMarkerSlot;

        public ArgumentsAndBlockTranslation(RubyNode block, RubyNode[] arguments, boolean isSplatted, FrameSlot frameOnStackMarkerSlot) {
            super();
            this.block = block;
            this.arguments = arguments;
            this.isSplatted = isSplatted;
            this.frameOnStackMarkerSlot = frameOnStackMarkerSlot;
        }

        public RubyNode getBlock() {
            return block;
        }

        public RubyNode[] getArguments() {
            return arguments;
        }

        public boolean isSplatted() {
            return isSplatted;
        }

        public FrameSlot getFrameOnStackMarkerSlot() {
            return frameOnStackMarkerSlot;
        }
    }

    public static final Object BAD_FRAME_SLOT = new Object();

    public Deque<Object> frameOnStackMarkerSlotStack = new ArrayDeque<>();

    protected ArgumentsAndBlockTranslation translateArgumentsAndBlock(SourceIndexLength sourceSection, ParseNode iterNode, ParseNode argsNode, String nameToSetWhenTranslatingBlock) {
        assert !(argsNode instanceof IterParseNode);

        final List<ParseNode> arguments = new ArrayList<>();
        ParseNode blockPassNode = null;

        boolean isSplatted = false;

        if (argsNode instanceof ListParseNode) {
            arguments.addAll(argsNode.childNodes());
        } else if (argsNode instanceof BlockPassParseNode) {
            final BlockPassParseNode blockPass = (BlockPassParseNode) argsNode;

            final ParseNode blockPassArgs = blockPass.getArgsNode();

            if (blockPassArgs instanceof ListParseNode) {
                arguments.addAll(blockPassArgs.childNodes());
            } else if (blockPassArgs instanceof ArgsCatParseNode) {
                arguments.add(blockPassArgs);
            } else if (blockPassArgs != null) {
                throw new UnsupportedOperationException("Don't know how to block pass " + blockPassArgs);
            }

            blockPassNode = blockPass.getBodyNode();
        } else if (argsNode instanceof SplatParseNode) {
            isSplatted = true;
            arguments.add(argsNode);
        } else if (argsNode instanceof ArgsCatParseNode) {
            isSplatted = true;
            arguments.add(argsNode);
        } else if (argsNode != null) {
            isSplatted = true;
            arguments.add(argsNode);
        }

        final RubyNode[] argumentsTranslated = new RubyNode[arguments.size()];
        for (int i = 0; i < arguments.size(); i++) {
            argumentsTranslated[i] = arguments.get(i).accept(this);
        }

        if (iterNode instanceof BlockPassParseNode) {
            blockPassNode = ((BlockPassParseNode) iterNode).getBodyNode();
        }

        currentCallMethodName = nameToSetWhenTranslatingBlock;


        final FrameSlot frameOnStackMarkerSlot;
        RubyNode blockTranslated;

        if (blockPassNode != null) {
            blockTranslated = ToProcNodeGen.create(blockPassNode.accept(this));
            blockTranslated.unsafeSetSourceSection(sourceSection);
            frameOnStackMarkerSlot = null;
        } else if (iterNode != null) {
            frameOnStackMarkerSlot = environment.declareVar(environment.allocateLocalTemp("frame_on_stack_marker"));
            frameOnStackMarkerSlotStack.push(frameOnStackMarkerSlot);

            try {
                blockTranslated = iterNode.accept(this);
            } finally {
                frameOnStackMarkerSlotStack.pop();
            }

            if (blockTranslated instanceof ObjectLiteralNode && ((ObjectLiteralNode) blockTranslated).getObject() == context.getCoreLibrary().getNil()) {
                blockTranslated = null;
            }
        } else {
            blockTranslated = null;
            frameOnStackMarkerSlot = null;
        }

        return new ArgumentsAndBlockTranslation(blockTranslated, argumentsTranslated, isSplatted, frameOnStackMarkerSlot);
    }

    @Override
    public RubyNode visitCaseNode(CaseParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        RubyNode elseNode = translateNodeOrNil(sourceSection, node.getElseNode());

        /*
         * There are two sorts of case - one compares a list of expressions against a value, the
         * other just checks a list of expressions for truth.
         */

        final RubyNode ret;

        if (node.getCaseNode() != null) {
            // Evaluate the case expression and store it in a local

            final String tempName = environment.allocateLocalTemp("case");

            final ReadLocalNode readTemp = environment.findLocalVarNode(tempName, source, sourceSection);

            final RubyNode assignTemp = readTemp.makeWriteNode(node.getCaseNode().accept(this));

            /*
             * Build an if expression from the whens and else. Work backwards because the first if
             * contains all the others in its else clause.
             */

            for (int n = node.getCases().size() - 1; n >= 0; n--) {
                final WhenParseNode when = (WhenParseNode) node.getCases().get(n);

                // Make a condition from the one or more expressions combined in an or expression

                final List<ParseNode> expressions;

                if (when.getExpressionNodes() instanceof ListParseNode && !(when.getExpressionNodes() instanceof ArrayParseNode)) {
                    expressions = when.getExpressionNodes().childNodes();
                } else {
                    expressions = Collections.singletonList(when.getExpressionNodes());
                }

                final List<RubyNode> comparisons = new ArrayList<>();

                for (ParseNode expressionNode : expressions) {
                    final RubyNode rubyExpression = expressionNode.accept(this);

                    final RubyNode receiver;
                    final RubyNode[] arguments;
                    final String method;
                    if (expressionNode instanceof SplatParseNode
                            || expressionNode instanceof ArgsCatParseNode
                            || expressionNode instanceof ArgsPushParseNode) {
                        receiver = new ObjectLiteralNode(context.getCoreLibrary().getTruffleModule());
                        receiver.unsafeSetSourceSection(sourceSection);
                        method = "when_splat";
                        arguments = new RubyNode[] { rubyExpression, NodeUtil.cloneNode(readTemp) };
                    } else {
                        receiver = rubyExpression;
                        method = "===";
                        arguments = new RubyNode[] { NodeUtil.cloneNode(readTemp) };
                    }
                    RubyCallNodeParameters callParameters = new RubyCallNodeParameters(receiver, method, null, arguments, false, true);
                    comparisons.add(Translator.withSourceSection(sourceSection, new RubyCallNode(callParameters)));
                }

                RubyNode conditionNode = comparisons.get(comparisons.size() - 1);

                // As with the if nodes, we work backwards to make it left associative

                for (int i = comparisons.size() - 2; i >= 0; i--) {
                    conditionNode = new OrNode(comparisons.get(i), conditionNode);
                }

                // Create the if node

                final RubyNode thenNode = translateNodeOrNil(sourceSection, when.getBodyNode());

                final IfElseNode ifNode = new IfElseNode(conditionNode, thenNode, elseNode);

                // This if becomes the else for the next if

                elseNode = ifNode;
            }

            final RubyNode ifNode = elseNode;

            // A top-level block assigns the temp then runs the if

            ret = sequence(sourceSection, Arrays.asList(assignTemp, ifNode));
        } else {
            for (int n = node.getCases().size() - 1; n >= 0; n--) {
                final WhenParseNode when = (WhenParseNode) node.getCases().get(n);

                // Make a condition from the one or more expressions combined in an or expression

                final List<ParseNode> expressions;

                if (when.getExpressionNodes() instanceof ListParseNode) {
                    expressions = when.getExpressionNodes().childNodes();
                } else {
                    expressions = Collections.singletonList(when.getExpressionNodes());
                }

                final List<RubyNode> tests = new ArrayList<>();

                for (ParseNode expressionNode : expressions) {
                    final RubyNode rubyExpression = expressionNode.accept(this);
                    tests.add(rubyExpression);
                }

                RubyNode conditionNode = tests.get(tests.size() - 1);

                // As with the if nodes, we work backwards to make it left associative

                for (int i = tests.size() - 2; i >= 0; i--) {
                    conditionNode = new OrNode(tests.get(i), conditionNode);
                }

                // Create the if node

                final RubyNode thenNode = when.getBodyNode().accept(this);

                final IfElseNode ifNode = new IfElseNode(conditionNode, thenNode, elseNode);

                // This if becomes the else for the next if

                elseNode = ifNode;
            }

            ret = elseNode;
        }

        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode openModule(SourceIndexLength sourceSection, RubyNode defineOrGetNode, String name, ParseNode bodyNode, boolean sclass) {
        final SourceSection fullSourceSection = sourceSection.toSourceSection(source);

        LexicalScope newLexicalScope = environment.pushLexicalScope();
        try {
            final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                    fullSourceSection,
                    newLexicalScope,
                    Arity.NO_ARGUMENTS,
                    null,
                    name,
                    sclass ? "class body" : "module body",
                    null,
                    false);

            final ReturnID returnId;

            if (sclass) {
                returnId = environment.getReturnID();
            } else {
                returnId = environment.getParseEnvironment().allocateReturnID();
            }

            final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(context, environment, environment.getParseEnvironment(),
                            returnId, true, true, true, sharedMethodInfo, name, 0, null);

            final BodyTranslator moduleTranslator = new BodyTranslator(currentNode, context, this, newEnvironment, source, parserContext, false);

            final ModuleBodyDefinitionNode definition = moduleTranslator.compileClassNode(sourceSection, name, bodyNode, sclass);

            return Translator.withSourceSection(sourceSection, new RunModuleDefinitionNode(newLexicalScope, definition, defineOrGetNode));
        } finally {
            environment.popLexicalScope();
        }
    }

    /**
     * Translates module and class nodes.
     * <p>
     * In Ruby, a module or class definition is somewhat like a method. It has a local scope and a value
     * for self, which is the module or class object that is being defined. Therefore for a module or
     * class definition we translate into a special method. We run that method with self set to be the
     * newly allocated module or class.
     * </p>
     */
    private ModuleBodyDefinitionNode compileClassNode(SourceIndexLength sourceSection, String name, ParseNode bodyNode, boolean sclass) {
        RubyNode body = translateNodeOrNil(sourceSection, bodyNode);

        if (environment.getFlipFlopStates().size() > 0) {
            body = sequence(sourceSection, Arrays.asList(initFlipFlopStates(sourceSection), body));
        }

        final RubyNode writeSelfNode = loadSelf(context, environment);
        body = sequence(sourceSection, Arrays.asList(writeSelfNode, body));

        final SourceSection fullSourceSection = sourceSection.toSourceSection(source);

        final RubyRootNode rootNode = new RubyRootNode(context, fullSourceSection, environment.getFrameDescriptor(), environment.getSharedMethodInfo(), body);

        final ModuleBodyDefinitionNode definitionNode = new ModuleBodyDefinitionNode(
                environment.getSharedMethodInfo().getName(),
                environment.getSharedMethodInfo(),
                Truffle.getRuntime().createCallTarget(rootNode),
                sclass,
                environment.isDynamicConstantLookup());

        definitionNode.unsafeSetSourceSection(sourceSection);

        return definitionNode;
    }

    @Override
    public RubyNode visitClassNode(ClassParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final String name = node.getCPath().getName();

        RubyNode lexicalParent = translateCPath(sourceSection, node.getCPath());

        final RubyNode superClass = node.getSuperNode() != null ? node.getSuperNode().accept(this) : null;
        final DefineClassNode defineOrGetClass = new DefineClassNode(name, lexicalParent, superClass);

        final RubyNode ret = openModule(sourceSection, defineOrGetClass, name, node.getBodyNode(), false);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitClassVarAsgnNode(ClassVarAsgnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode rhs = node.getValueNode().accept(this);

        final RubyNode ret = new WriteClassVariableNode(getLexicalScopeNode("set dynamic class variable", sourceSection), node.getName(), rhs);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitClassVarNode(ClassVarParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret = new ReadClassVariableNode(getLexicalScopeNode("class variable lookup", sourceSection), node.getName());
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitColon2Node(Colon2ParseNode node) {
        // Qualified constant access, as in Mod::CONST
        if (!(node instanceof Colon2ConstParseNode)) {
            throw new UnsupportedOperationException(node.toString());
        }

        final SourceIndexLength sourceSection = node.getPosition();
        final String name = ConstantReplacer.replacementName(source, node.getName());

        final RubyNode lhs = node.getLeftNode().accept(this);

        final RubyNode ret = new ReadConstantNode(lhs, name);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitColon3Node(Colon3ParseNode node) {
        // Root namespace constant access, as in ::Foo

        final SourceIndexLength sourceSection = node.getPosition();
        final String name = ConstantReplacer.replacementName(source, node.getName());

        final ObjectLiteralNode root = new ObjectLiteralNode(context.getCoreLibrary().getObjectClass());
        root.unsafeSetSourceSection(sourceSection);

        final RubyNode ret = new ReadConstantNode(root, name);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode translateCPath(SourceIndexLength sourceSection, Colon3ParseNode node) {
        final RubyNode ret;

        if (node instanceof Colon2ImplicitParseNode) { // use current lexical scope
            ret = getLexicalScopeModuleNode("dynamic constant lookup", sourceSection);
            ret.unsafeSetSourceSection(sourceSection);
        } else if (node instanceof Colon2ConstParseNode) { // A::B
            ret = node.childNodes().get(0).accept(this);
        } else { // Colon3ParseNode: on top-level (Object)
            ret = new ObjectLiteralNode(context.getCoreLibrary().getObjectClass());
            ret.unsafeSetSourceSection(sourceSection);
        }

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitComplexNode(ComplexParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode ret = translateRationalComplex(sourceSection, "Complex",
                new IntegerFixnumLiteralNode(0),
                node.getNumber().accept(this));

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitConstDeclNode(ConstDeclParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        RubyNode rhs = node.getValueNode().accept(this);

        final RubyNode moduleNode;
        ParseNode constNode = node.getConstNode();
        if (constNode == null || constNode instanceof Colon2ImplicitParseNode) {
            moduleNode = getLexicalScopeModuleNode("set dynamic constant", sourceSection);
            moduleNode.unsafeSetSourceSection(sourceSection);
        } else if (constNode instanceof Colon2ConstParseNode) {
            constNode = ((Colon2ParseNode) constNode).getLeftNode(); // Misleading doc, we only want the defined part.
            moduleNode = constNode.accept(this);
        } else if (constNode instanceof Colon3ParseNode) {
            moduleNode = new ObjectLiteralNode(context.getCoreLibrary().getObjectClass());
        } else {
            throw new UnsupportedOperationException();
        }

        final RubyNode ret = new WriteConstantNode(node.getName(), moduleNode, rhs);
        ret.unsafeSetSourceSection(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode getLexicalScopeModuleNode(String kind, SourceIndexLength sourceSection) {
        if (environment.isDynamicConstantLookup()) {
            if (context.getOptions().LOG_DYNAMIC_CONSTANT_LOOKUP) {
                Log.LOGGER.info(() -> kind + " at " + RubyLanguage.fileLine(sourceSection.toSourceSection(source)));
            }
            return new DynamicLexicalScopeNode();
        } else {
            return new LexicalScopeNode(environment.getLexicalScope());
        }
    }

    private RubyNode getLexicalScopeNode(String kind, SourceIndexLength sourceSection) {
        if (environment.isDynamicConstantLookup()) {
            if (context.getOptions().LOG_DYNAMIC_CONSTANT_LOOKUP) {
                Log.LOGGER.info(() -> kind + " at " + RubyLanguage.fileLine(sourceSection.toSourceSection(source)));
            }
            return new GetDynamicLexicalScopeNode();
        } else {
            return new ObjectLiteralNode(environment.getLexicalScope());
        }
    }

    private String getSourcePath(SourceIndexLength sourceSection) {
        if (sourceSection == null) {
            return "(unknown)";
        }

        if (source == null) {
            return "(unknown)";
        }

        final String path = source.getName();

        if (path == null) {
            return source.getName();
        }

        return path;
    }

    private String corePath() {
        return environment.getParseEnvironment().getCorePath();
    }

    @Override
    public RubyNode visitConstNode(ConstParseNode node) {
        // Unqualified constant access, as in CONST
        final SourceIndexLength sourceSection = node.getPosition();

        final String name = ConstantReplacer.replacementName(source, node.getName());

        final RubyNode ret;
        if (environment.isDynamicConstantLookup()) {
            if (context.getOptions().LOG_DYNAMIC_CONSTANT_LOOKUP) {
                Log.LOGGER.info(() -> "dynamic constant lookup at " + RubyLanguage.fileLine(sourceSection.toSourceSection(source)));
            }
            ret = new ReadConstantWithDynamicScopeNode(name);
        } else {
            final LexicalScope lexicalScope = environment.getLexicalScope();
            ret = new ReadConstantWithLexicalScopeNode(lexicalScope, name);
        }
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);

    }

    @Override
    public RubyNode visitDAsgnNode(DAsgnParseNode node) {
        final RubyNode ret = new LocalAsgnParseNode(node.getPosition(), node.getName(), node.getDepth(), node.getValueNode()).accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitDRegxNode(DRegexpParseNode node) {
        SourceIndexLength sourceSection = node.getPosition();

        final List<RubyNode> children = new ArrayList<>();

        for (ParseNode child : node.children()) {
            children.add(child.accept(this));
        }

        final InterpolatedRegexpNode i = new InterpolatedRegexpNode(children.toArray(new RubyNode[children.size()]), node.getOptions());
        i.unsafeSetSourceSection(sourceSection);

        if (node.getOptions().isOnce()) {
            final RubyNode ret = new OnceNode(i);
            ret.unsafeSetSourceSection(sourceSection);
            return addNewlineIfNeeded(node, ret);
        }

        return addNewlineIfNeeded(node, i);
    }

    @Override
    public RubyNode visitDStrNode(DStrParseNode node) {
        final RubyNode ret = translateInterpolatedString(node.getPosition(), node.children());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitDSymbolNode(DSymbolParseNode node) {
        SourceIndexLength sourceSection = node.getPosition();

        final RubyNode stringNode = translateInterpolatedString(sourceSection, node.children());

        final RubyNode ret = StringToSymbolNodeGen.create(stringNode);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode translateInterpolatedString(SourceIndexLength sourceSection, ParseNode[] childNodes) {
        final ToSNode[] children = new ToSNode[childNodes.length];

        for (int i = 0; i < childNodes.length; i++) {
            children[i] = ToSNodeGen.create(childNodes[i].accept(this));
        }

        final RubyNode ret = new InterpolatedStringNode(children);
        ret.unsafeSetSourceSection(sourceSection);
        return ret;
    }

    @Override
    public RubyNode visitDVarNode(DVarParseNode node) {
        final String name = node.getName();
        RubyNode readNode = environment.findLocalVarNode(name, source, node.getPosition());

        if (readNode == null) {
            // If we haven't seen this dvar before it's possible that it's a block local variable

            final int depth = node.getDepth();

            TranslatorEnvironment e = environment;

            for (int n = 0; n < depth; n++) {
                e = e.getParent();
            }

            e.declareVar(name);

            // Searching for a local variable must start at the base environment, even though we may have determined
            // the variable should be declared in a parent frame descriptor.  This is so the search can determine
            // whether to return a ReadLocalVariableNode or a ReadDeclarationVariableNode and potentially record the
            // fact that a declaration frame is needed.
            readNode = environment.findLocalVarNode(name, source, node.getPosition());
        }

        return addNewlineIfNeeded(node, readNode);
    }

    @Override
    public RubyNode visitDXStrNode(DXStrParseNode node) {
        final DStrParseNode string = new DStrParseNode(node.getPosition(), node.getEncoding());
        string.addAll(node);
        final ParseNode argsNode = buildArrayNode(node.getPosition(), string);
        final ParseNode callNode = new FCallParseNode(node.getPosition(), "`", argsNode, null);
        copyNewline(node, callNode);
        final RubyNode ret = callNode.accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitDefinedNode(DefinedParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret = new DefinedNode(node.getExpressionNode().accept(this));
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitDefnNode(DefnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode classNode = new RaiseIfFrozenNode(new GetDefaultDefineeNode());

        String methodName = node.getName();

        final RubyNode ret = translateMethodDefinition(sourceSection, classNode, methodName, node.getArgsNode(), node, node.getBodyNode(), false);

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitDefsNode(DefsParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode objectNode = node.getReceiverNode().accept(this);

        final SingletonClassNode singletonClassNode = SingletonClassNodeGen.create(objectNode);
        singletonClassNode.unsafeSetSourceSection(sourceSection);

        final RubyNode ret = translateMethodDefinition(sourceSection, singletonClassNode, node.getName(), node.getArgsNode(), node, node.getBodyNode(), true);

        return addNewlineIfNeeded(node, ret);
    }

    protected RubyNode translateMethodDefinition(SourceIndexLength sourceSection, RubyNode classNode, String methodName,
            ArgsParseNode argsNode, MethodDefParseNode defNode, ParseNode bodyNode, boolean isDefs) {
        final Arity arity = MethodTranslator.getArity(argsNode);
        final ArgumentDescriptor[] argumentDescriptors = Helpers.argsNodeToArgumentDescriptors(argsNode);

        final boolean alwaysClone = MethodTranslator.isPrimitive(bodyNode);

        final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                sourceSection.toSourceSection(source),
                environment.getLexicalScopeOrNull(),
                arity,
                null,
                methodName,
                null,
                argumentDescriptors,
                alwaysClone);

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(
                        context, environment, environment.getParseEnvironment(), environment.getParseEnvironment().allocateReturnID(), true, true, false, sharedMethodInfo, methodName, 0, null);

        // ownScopeForAssignments is the same for the defined method as the current one.

        final MethodTranslator methodCompiler = new MethodTranslator(currentNode, context, this, newEnvironment, false, source, parserContext, argsNode);

        final MethodDefinitionNode methodDefinitionNode = methodCompiler.compileMethodNode(sourceSection, methodName, defNode, bodyNode, sharedMethodInfo);

        final RubyNode visibilityNode;
        if (isDefs) {
            visibilityNode = new ObjectLiteralNode(Visibility.PUBLIC);
        } else {
            visibilityNode = new GetCurrentVisibilityNode();
        }

        return withSourceSection(sourceSection, AddMethodNodeGen.create(isDefs, true, classNode, methodDefinitionNode, visibilityNode));
    }

    @Override
    public RubyNode visitDotNode(DotParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode begin = node.getBeginNode().accept(this);
        final RubyNode end = node.getEndNode().accept(this);
        final RubyNode rangeClass = new ObjectLiteralNode(context.getCoreLibrary().getRangeClass());
        final RubyNode isExclusive = new ObjectLiteralNode(node.isExclusive());

        final RubyNode ret = RangeNodesFactory.NewNodeFactory.create(rangeClass, begin, end, isExclusive);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitEncodingNode(EncodingParseNode node) {
        SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret = new ObjectLiteralNode(context.getEncodingManager().getRubyEncoding(node.getEncoding()));
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitEnsureNode(EnsureParseNode node) {
        final RubyNode tryPart = node.getBodyNode().accept(this);
        final RubyNode ensurePart = node.getEnsureNode().accept(this);
        final RubyNode ret = new EnsureNode(tryPart, ensurePart);
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitEvStrNode(EvStrParseNode node) {
        final RubyNode ret;

        if (node.getBody() == null) {
            final SourceIndexLength sourceSection = node.getPosition();
            ret = new ObjectLiteralNode(StringOperations.createString(context, RopeConstants.EMPTY_ASCII_8BIT_ROPE));
            ret.unsafeSetSourceSection(sourceSection);
        } else {
            ret = node.getBody().accept(this);
        }

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitFCallNode(FCallParseNode node) {
        final ParseNode receiver = new SelfParseNode(node.getPosition());
        final CallParseNode callNode = new CallParseNode(node.getPosition(), receiver, node.getName(), node.getArgsNode(), node.getIterNode());
        copyNewline(node, callNode);
        return translateCallNode(callNode, true, false, false);
    }

    @Override
    public RubyNode visitFalseNode(FalseParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret = new BooleanLiteralNode(false);
        ret.unsafeSetSourceSection(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitFixnumNode(FixnumParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final long value = node.getValue();
        final RubyNode ret;

        if (CoreLibrary.fitsIntoInteger(value)) {
            ret = new IntegerFixnumLiteralNode((int) value);
        } else {
            ret = new LongFixnumLiteralNode(value);
        }
        ret.unsafeSetSourceSection(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitFlipNode(FlipParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode begin = node.getBeginNode().accept(this);
        final RubyNode end = node.getEndNode().accept(this);

        final FlipFlopStateNode stateNode = createFlipFlopState(sourceSection, 0);

        final RubyNode ret = new FlipFlopNode(begin, end, stateNode, node.isExclusive());
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    protected FlipFlopStateNode createFlipFlopState(SourceIndexLength sourceSection, int depth) {
        final FrameSlot frameSlot = environment.declareVar(environment.allocateLocalTemp("flipflop"));
        environment.getFlipFlopStates().add(frameSlot);

        if (depth == 0) {
            return new LocalFlipFlopStateNode(frameSlot);
        } else {
            return new DeclarationFlipFlopStateNode(depth, frameSlot);
        }
    }

    @Override
    public RubyNode visitFloatNode(FloatParseNode node) {
        final RubyNode ret = new FloatLiteralNode(node.getValue());
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitForNode(ForParseNode node) {
        /**
         * A Ruby for-loop, such as:
         *
         * <pre>
         * for x in y
         *     z = x
         *     puts z
         * end
         * </pre>
         *
         * naively desugars to:
         *
         * <pre>
         * y.each do |x|
         *     z = x
         *     puts z
         * end
         * </pre>
         *
         * The main difference is that z is always going to be local to the scope outside the block,
         * so it's a bit more like:
         *
         * <pre>
         * z = nil unless z is already defined
         * y.each do |x|
         *    z = x
         *    puts x
         * end
         * </pre>
         *
         * Which forces z to be defined in the correct scope. The parser already correctly calls z a
         * local, but then that causes us a problem as if we're going to translate to a block we
         * need a formal parameter - not a local variable. My solution to this is to add a
         * temporary:
         *
         * <pre>
         * z = nil unless z is already defined
         * y.each do |temp|
         *    x = temp
         *    z = x
         *    puts x
         * end
         * </pre>
         *
         * We also need that temp because the expression assigned in the for could be index
         * assignment, multiple assignment, or whatever:
         *
         * <pre>
         * for x[0] in y
         *     z = x[0]
         *     puts z
         * end
         * </pre>
         *
         * http://blog.grayproductions.net/articles/the_evils_of_the_for_loop
         * http://stackoverflow.com/questions/3294509/for-vs-each-in-ruby
         *
         * The other complication is that normal locals should be defined in the enclosing scope,
         * unlike a normal block. We do that by setting a flag on this translator object when we
         * visit the new iter, translatingForStatement, which we recognise when visiting an iter
         * node.
         *
         * Finally, note that JRuby's terminology is strange here. Normally 'iter' is a different
         * term for a block. Here, JRuby calls the object being iterated over the 'iter'.
         */

        final String temp = environment.allocateLocalTemp("for");

        final ParseNode receiver = node.getIterNode();

        /*
         * The x in for x in ... is like the nodes in multiple assignment - it has a dummy RHS which
         * we need to replace with our temp. Just like in multiple assignment this is really awkward
         * with the JRuby AST.
         */

        final LocalVarParseNode readTemp = new LocalVarParseNode(node.getPosition(), 0, temp);
        final ParseNode forVar = node.getVarNode();
        final ParseNode assignTemp = setRHS(forVar, readTemp);

        final BlockParseNode bodyWithTempAssign = new BlockParseNode(node.getPosition());
        bodyWithTempAssign.add(assignTemp);
        bodyWithTempAssign.add(node.getBodyNode());

        final ArgumentParseNode blockVar = new ArgumentParseNode(node.getPosition(), temp);
        final ListParseNode blockArgsPre = new ListParseNode(node.getPosition(), blockVar);
        final ArgsParseNode blockArgs = new ArgsParseNode(node.getPosition(), blockArgsPre, null, null, null, null, null, null);
        final IterParseNode block = new IterParseNode(node.getPosition(), blockArgs, node.getScope(), bodyWithTempAssign);

        final CallParseNode callNode = new CallParseNode(node.getPosition(), receiver, "each", null, block);
        copyNewline(node, callNode);

        translatingForStatement = true;
        final RubyNode translated = callNode.accept(this);
        translatingForStatement = false;

        return addNewlineIfNeeded(node, translated);
    }

    private final ParserSupport parserSupport;

    private ParseNode setRHS(ParseNode node, ParseNode rhs) {
        if (node instanceof AssignableParseNode || node instanceof org.truffleruby.parser.ast.IArgumentNode) {
            return parserSupport.node_assign(node, rhs);
        } else {
            throw new UnsupportedOperationException("Don't know how to set the RHS of a " + node.getClass().getName());
        }
    }

    private RubyNode translateDummyAssignment(ParseNode dummyAssignment, final RubyNode rhs) {
        // The JRuby AST includes assignment nodes without a proper value,
        // so we need to patch them to include the proper rhs value to translate them correctly.

        if (dummyAssignment instanceof StarParseNode) {
            // Nothing to assign to, just execute the RHS
            return rhs;
        } else if (dummyAssignment instanceof AssignableParseNode || dummyAssignment instanceof org.truffleruby.parser.ast.IArgumentNode) {
            final ParseNode wrappedRHS = new ParseNode(dummyAssignment.getPosition()) {
                @SuppressWarnings("unchecked")
                @Override
                public <T> T accept(NodeVisitor<T> visitor) {
                    return (T) rhs;
                }

                @Override
                public List<ParseNode> childNodes() {
                    return Collections.emptyList();
                }

                @Override
                public org.truffleruby.parser.ast.NodeType getNodeType() {
                    return org.truffleruby.parser.ast.NodeType.FIXNUMNODE; // since we behave like a value
                }
            };

            return setRHS(dummyAssignment, wrappedRHS).accept(this);
        } else {
            throw new UnsupportedOperationException("Don't know how to translate the dummy asgn " + dummyAssignment.getClass().getName());
        }
    }

    @Override
    public RubyNode visitGlobalAsgnNode(GlobalAsgnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        RubyNode rhs = node.getValueNode().accept(this);

        String name = node.getName();

        if (GLOBAL_VARIABLE_ALIASES.containsKey(name)) {
            name = GLOBAL_VARIABLE_ALIASES.get(name);
        }

        switch (name) {
            case "$~":
                rhs = new CheckMatchVariableTypeNode(rhs);
                break;
            case "$0":
                rhs = new CheckProgramNameVariableTypeNode(rhs);
                rhs.unsafeSetSourceSection(sourceSection);
                break;
            case "$/":
                rhs = new CheckRecordSeparatorVariableTypeNode(rhs);
                rhs.unsafeSetSourceSection(sourceSection);
                break;
            case "$,":
                rhs = new CheckOutputSeparatorVariableTypeNode(rhs);
                rhs.unsafeSetSourceSection(sourceSection);
                break;
            case "$SAFE":
                rhs = new CheckSafeLevelNode(rhs);
                rhs.unsafeSetSourceSection(sourceSection);
                break; 
            case "$stdout":
                rhs = new CheckStdoutVariableTypeNode(rhs);
                rhs.unsafeSetSourceSection(sourceSection);
                break;
            case "$VERBOSE":
                rhs = new UpdateVerbosityNode(rhs);
                rhs.unsafeSetSourceSection(sourceSection);
                break;
            case "$@":
                // $@ is a special-case and doesn't write directly to an ivar field in the globals object.
                // Instead, it writes to the backtrace field of the thread-local $! value.
                return withSourceSection(sourceSection, new UpdateLastBacktraceNode(rhs));
        }

        final boolean inCore = getSourcePath(node.getValueNode().getPosition()).startsWith(corePath());

        if (!inCore && READ_ONLY_GLOBAL_VARIABLES.contains(name)) {
            return addNewlineIfNeeded(node, withSourceSection(sourceSection, new WriteReadOnlyGlobalNode(name, rhs)));
        }

        if (THREAD_LOCAL_GLOBAL_VARIABLES.contains(name)) {
            final GetThreadLocalsObjectNode getThreadLocalsObjectNode = GetThreadLocalsObjectNodeGen.create();
            getThreadLocalsObjectNode.unsafeSetSourceSection(sourceSection);
            return addNewlineIfNeeded(node, withSourceSection(sourceSection, new WriteInstanceVariableNode(name, getThreadLocalsObjectNode, rhs)));
        } else if (FRAME_LOCAL_GLOBAL_VARIABLES.contains(name)) {
            final ReadLocalNode localVarNode = environment.findFrameLocalGlobalVarNode(name, source, sourceSection);
            final RubyNode assignment;
            if (THREAD_AND_FRAME_LOCAL_GLOBAL_VARIABLES.contains(name)) {
                assignment = new SetInThreadAndFrameLocalStorageNode(localVarNode, rhs);
                assignment.unsafeSetSourceSection(sourceSection);
            } else {
                assignment = localVarNode.makeWriteNode(rhs);
            }

            return addNewlineIfNeeded(node, assignment);
        } else {
            final RubyNode writeGlobalVariableNode = withSourceSection(sourceSection, WriteGlobalVariableNodeGen.create(name, rhs));

            final RubyNode translated;

            if (name.equals("$0")) {
                // Call Process.setproctitle
                RubyNode processClass = new ObjectLiteralNode(context.getCoreLibrary().getProcessModule());
                translated = new RubyCallNode(new RubyCallNodeParameters(processClass, "setproctitle", null,
                                new RubyNode[]{writeGlobalVariableNode}, false, false));
                translated.unsafeSetSourceSection(sourceSection);
            } else {
                translated = writeGlobalVariableNode;
            }

            return addNewlineIfNeeded(node, translated);
        }
    }

    @Override
    public RubyNode visitGlobalVarNode(GlobalVarParseNode node) {
        String name = node.getName();

        if (GLOBAL_VARIABLE_ALIASES.containsKey(name)) {
            name = GLOBAL_VARIABLE_ALIASES.get(name);
        }

        final SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret;

        if (FRAME_LOCAL_GLOBAL_VARIABLES.contains(name)) {
            // Assignment is implicit for many of these, so we need to declare when we use

            RubyNode readNode = environment.findFrameLocalGlobalVarNode(name, source, sourceSection);

            if (THREAD_AND_FRAME_LOCAL_GLOBAL_VARIABLES.contains(name)) {
                readNode = new GetFromThreadAndFrameLocalStorageNode(readNode);
                readNode.unsafeSetSourceSection(sourceSection);
            }

            ret = readNode;
        } else if (THREAD_LOCAL_GLOBAL_VARIABLES.contains(name)) {
            ret = new ReadThreadLocalGlobalVariableNode(name, ALWAYS_DEFINED_GLOBALS.contains(name));
            ret.unsafeSetSourceSection(sourceSection);
        } else if (name.equals("$@")) {
            // $@ is a special-case and doesn't read directly from an ivar field in the globals object.
            // Instead, it reads the backtrace field of the thread-local $! value.
            ret = new ReadLastBacktraceNode();
            ret.unsafeSetSourceSection(sourceSection);
        } else {
            ret = ReadGlobalVariableNodeGen.create(name);
            ret.unsafeSetSourceSection(sourceSection);
        }

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitHashNode(HashParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final List<RubyNode> hashConcats = new ArrayList<>();

        final List<RubyNode> keyValues = new ArrayList<>();

        for (ParseNodeTuple pair : node.getPairs()) {
            if (pair.getKey() == null) {
                // This null case is for splats {a: 1, **{b: 2}, c: 3}
                final RubyNode hashLiteralSoFar = HashLiteralNode.create(context, keyValues.toArray(new RubyNode[keyValues.size()]));
                hashConcats.add(hashLiteralSoFar);
                hashConcats.add(new EnsureSymbolKeysNode(
                    HashCastNodeGen.create(pair.getValue().accept(this))));
                keyValues.clear();
            } else {
                keyValues.add(pair.getKey().accept(this));

                if (pair.getValue() == null) {
                    keyValues.add(nilNode(sourceSection));
                } else {
                    keyValues.add(pair.getValue().accept(this));
                }
            }
        }

        final RubyNode hashLiteralSoFar = HashLiteralNode.create(context, keyValues.toArray(new RubyNode[keyValues.size()]));
        hashConcats.add(hashLiteralSoFar);

        if (hashConcats.size() == 1) {
            final RubyNode ret = hashConcats.get(0);
            return addNewlineIfNeeded(node, ret);
        }

        final RubyNode ret = new ConcatHashLiteralNode(hashConcats.toArray(new RubyNode[hashConcats.size()]));
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitIfNode(IfParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode condition = translateNodeOrNil(sourceSection, node.getCondition());

        ParseNode thenBody = node.getThenBody();
        ParseNode elseBody = node.getElseBody();

        final RubyNode ret;

        if (thenBody != null && elseBody != null) {
            final RubyNode thenBodyTranslated = thenBody.accept(this);
            final RubyNode elseBodyTranslated = elseBody.accept(this);
            ret = new IfElseNode(condition, thenBodyTranslated, elseBodyTranslated);
            ret.unsafeSetSourceSection(sourceSection);
        } else if (thenBody != null) {
            final RubyNode thenBodyTranslated = thenBody.accept(this);
            ret = new IfNode(condition, thenBodyTranslated);
            ret.unsafeSetSourceSection(sourceSection);
        } else if (elseBody != null) {
            final RubyNode elseBodyTranslated = elseBody.accept(this);
            ret = new UnlessNode(condition, elseBodyTranslated);
            ret.unsafeSetSourceSection(sourceSection);
        } else {
            ret = sequence(sourceSection, Arrays.asList(condition, new NilLiteralNode(true)));
        }

        return ret; // no addNewlineIfNeeded(node, ret) as the condition will already have a newline
    }

    @Override
    public RubyNode visitInstAsgnNode(InstAsgnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final String name = node.getName();

        final RubyNode rhs;
        if (node.getValueNode() == null) {
            rhs = new DeadNode(new Exception("null RHS of instance variable assignment"));
            rhs.unsafeSetSourceSection(sourceSection);
        } else {
            rhs = node.getValueNode().accept(this);
        }

        RubyNode self = new SelfNode(environment.getFrameDescriptor());
        self = new RaiseIfFrozenNode(self);
        final RubyNode ret = new WriteInstanceVariableNode(name, self, rhs);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitInstVarNode(InstVarParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final String name = node.getName();

        // About every case will use a SelfParseNode, just don't it use more than once.
        final SelfNode self = new SelfNode(environment.getFrameDescriptor());

        final RubyNode ret = new ReadInstanceVariableNode(name, self);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitIterNode(IterParseNode node) {
        return translateBlockLikeNode(node, false);
    }

    @Override
    public RubyNode visitLambdaNode(LambdaParseNode node) {
        return translateBlockLikeNode(node, true);
    }

    private RubyNode translateBlockLikeNode(IterParseNode node, boolean isLambda) {
        final SourceIndexLength sourceSection = node.getPosition();
        final ArgsParseNode argsNode = node.getArgsNode();

        // Unset this flag for any for any blocks within the for statement's body
        final boolean hasOwnScope = isLambda || !translatingForStatement;

        final boolean isProc = !isLambda;

        final SharedMethodInfo sharedMethodInfo = new SharedMethodInfo(
                sourceSection.toSourceSection(source),
                environment.getLexicalScopeOrNull(),
                MethodTranslator.getArity(argsNode),
                null,
                null,
                isLambda ? "lambda" : getIdentifierInNewEnvironment(true, currentCallMethodName),
                Helpers.argsNodeToArgumentDescriptors(argsNode),
                false);

        final String namedMethodName = isLambda ? sharedMethodInfo.getName(): environment.getNamedMethodName();

        final ParseEnvironment parseEnvironment = environment.getParseEnvironment();
        final ReturnID returnID = isLambda ? parseEnvironment.allocateReturnID() : environment.getReturnID();

        final TranslatorEnvironment newEnvironment = new TranslatorEnvironment(
                context, environment, parseEnvironment, returnID, hasOwnScope, false,
                        false, sharedMethodInfo, namedMethodName, environment.getBlockDepth() + 1, parseEnvironment.allocateBreakID());
        final MethodTranslator methodCompiler = new MethodTranslator(currentNode, context, this, newEnvironment, true, source, parserContext, argsNode);

        if (isProc) {
            methodCompiler.translatingForStatement = translatingForStatement;
        }

        methodCompiler.frameOnStackMarkerSlotStack = frameOnStackMarkerSlotStack;

        final ProcType type = isLambda ? ProcType.LAMBDA : ProcType.PROC;

        if (isLambda) {
            frameOnStackMarkerSlotStack.push(BAD_FRAME_SLOT);
        }

        final RubyNode definitionNode;

        try {
            definitionNode = methodCompiler.compileBlockNode(sourceSection, sharedMethodInfo.getName(), node.getBodyNode(), sharedMethodInfo, type, node.getScope().getVariables());
        } finally {
            if (isLambda) {
                frameOnStackMarkerSlotStack.pop();
            }
        }

        return addNewlineIfNeeded(node, definitionNode);
    }

    @Override
    public RubyNode visitLocalAsgnNode(LocalAsgnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        final String name = node.getName();

        if (environment.getNeverAssignInParentScope()) {
            environment.declareVar(name);
        }

        ReadLocalNode lhs = environment.findLocalVarNode(name, source, sourceSection);

        if (lhs == null) {
            TranslatorEnvironment environmentToDeclareIn = environment;
            while (!environmentToDeclareIn.hasOwnScopeForAssignments()) {
                environmentToDeclareIn = environmentToDeclareIn.getParent();
            }
            environmentToDeclareIn.declareVar(name);

            lhs = environment.findLocalVarNode(name, source, sourceSection);

            if (lhs == null) {
                throw new RuntimeException("shouldn't be here");
            }
        }

        RubyNode rhs;

        if (node.getValueNode() == null) {
            rhs = new DeadNode(new Exception());
            rhs.unsafeSetSourceSection(sourceSection);
        } else {
            rhs = node.getValueNode().accept(this);
        }

        final RubyNode ret = lhs.makeWriteNode(rhs);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitLocalVarNode(LocalVarParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final String name = node.getName();

        RubyNode readNode = environment.findLocalVarNode(name, source, sourceSection);

        if (readNode == null) {
            /*

              This happens for code such as:

                def destructure4r((*c,d))
                    [c,d]
                end

               We're going to just assume that it should be there and add it...
             */

            environment.declareVar(name);
            readNode = environment.findLocalVarNode(name, source, sourceSection);
        }

        return addNewlineIfNeeded(node, readNode);
    }

    @Override
    public RubyNode visitMatchNode(MatchParseNode node) {
        // Triggered when a Regexp literal is used as a conditional's value.

        final ParseNode argsNode = buildArrayNode(node.getPosition(), new GlobalVarParseNode(node.getPosition(), "$_"));
        final ParseNode callNode = new CallParseNode(node.getPosition(), node.getRegexpNode(), "=~", argsNode, null);
        copyNewline(node, callNode);
        final RubyNode ret = callNode.accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitMatch2Node(Match2ParseNode node) {
        // Triggered when a Regexp literal is the LHS of an expression.

        if (node.getReceiverNode() instanceof RegexpParseNode) {
            final RegexpParseNode regexpNode = (RegexpParseNode) node.getReceiverNode();
            final byte[] bytes = regexpNode.getValue().getBytes();
            final Regex regex = new Regex(bytes, 0, bytes.length, regexpNode.getOptions().toOptions(), regexpNode.getEncoding(), Syntax.RUBY);

            if (regex.numberOfNames() > 0) {
                for (Iterator<NameEntry> i = regex.namedBackrefIterator(); i.hasNext(); ) {
                    final NameEntry e = i.next();
                    final String name = new String(e.name, e.nameP, e.nameEnd - e.nameP, StandardCharsets.UTF_8).intern();

                    TranslatorEnvironment environmentToDeclareIn = environment;
                    while (!environmentToDeclareIn.hasOwnScopeForAssignments()) {
                        environmentToDeclareIn = environmentToDeclareIn.getParent();
                    }
                    environmentToDeclareIn.declareVar(name);
                }
            }
        }

        final ParseNode argsNode = buildArrayNode(node.getPosition(), node.getValueNode());
        final ParseNode callNode = new CallParseNode(node.getPosition(), node.getReceiverNode(), "=~", argsNode, null);
        copyNewline(node, callNode);
        final RubyNode ret = callNode.accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitMatch3Node(Match3ParseNode node) {
        // Triggered when a Regexp literal is the RHS of an expression.

        final ParseNode argsNode = buildArrayNode(node.getPosition(), node.getValueNode());
        final ParseNode callNode = new CallParseNode(node.getPosition(), node.getReceiverNode(), "=~", argsNode, null);
        copyNewline(node, callNode);
        final RubyNode ret = callNode.accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitModuleNode(ModuleParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final String name = node.getCPath().getName();

        RubyNode lexicalParent = translateCPath(sourceSection, node.getCPath());

        final DefineModuleNode defineModuleNode = DefineModuleNodeGen.create(name, lexicalParent);

        final RubyNode ret = openModule(sourceSection, defineModuleNode, name, node.getBodyNode(), false);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitMultipleAsgnNode(MultipleAsgnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final ListParseNode preArray = node.getPre();
        final ListParseNode postArray = node.getPost();
        final ParseNode rhs = node.getValueNode();

        RubyNode rhsTranslated;

        if (rhs == null) {
            throw new UnsupportedOperationException("null rhs");
        } else {
            rhsTranslated = rhs.accept(this);
        }

        final RubyNode result;

        // TODO CS 5-Jan-15 we shouldn't be doing this kind of low level optimisation or pattern matching - EA should do it for us

        if (preArray != null
                && node.getPost() == null
                && node.getRest() == null
                && rhsTranslated instanceof ArrayLiteralNode
                && ((ArrayLiteralNode) rhsTranslated).getSize() == preArray.size()) {
            /*
             * We can deal with this common case be rewriting
             *
             * a, b = c, d
             *
             * as
             *
             * temp1 = c; temp2 = d; a = temp1; b = temp2
             *
             * We can't just do
             *
             * a = c; b = d
             *
             * As we don't know if d depends on the original value of a.
             *
             * We also need to return an array [c, d], but we make that result elidable so it isn't
             * executed if it isn't actually demanded.
             */

            final ArrayLiteralNode rhsArrayLiteral = (ArrayLiteralNode) rhsTranslated;
            final int assignedValuesCount = preArray.size();

            final RubyNode[] sequence = new RubyNode[assignedValuesCount * 2];

            final RubyNode[] tempValues = new RubyNode[assignedValuesCount];

            for (int n = 0; n < assignedValuesCount; n++) {
                final String tempName = environment.allocateLocalTemp("multi");
                final ReadLocalNode readTemp = environment.findLocalVarNode(tempName, source, sourceSection);
                final RubyNode assignTemp = readTemp.makeWriteNode(rhsArrayLiteral.stealNode(n));
                final RubyNode assignFinalValue = translateDummyAssignment(preArray.get(n), NodeUtil.cloneNode(readTemp));

                sequence[n] = assignTemp;
                sequence[assignedValuesCount + n] = assignFinalValue;

                tempValues[n] = NodeUtil.cloneNode(readTemp);
            }

            final RubyNode blockNode = sequence(sourceSection, Arrays.asList(sequence));

            final ArrayLiteralNode arrayNode = ArrayLiteralNode.create(tempValues);
            arrayNode.unsafeSetSourceSection(sourceSection);
            result = new ElidableResultNode(blockNode, arrayNode);
        } else if (preArray != null) {
            /*
             * The other simple case is
             *
             * a, b, c = x
             *
             * If x is an array, then it's
             *
             * a = x[0] etc
             *
             * If x isn't an array then it's
             *
             * a, b, c = [x, nil, nil]
             *
             * Which I believe is the same effect as
             *
             * a, b, c, = *x
             *
             * So we insert the splat cast node, even though it isn't there.
             *
             * In either case, we return the RHS
             */

            final List<RubyNode> sequence = new ArrayList<>();

            /*
             * Store the RHS in a temp.
             */

            final String tempRHSName = environment.allocateLocalTemp("rhs");
            final RubyNode writeTempRHS = environment.findLocalVarNode(tempRHSName, source, sourceSection).makeWriteNode(rhsTranslated);
            sequence.add(writeTempRHS);

            /*
             * Create a temp for the array.
             */

            final String tempName = environment.allocateLocalTemp("array");

            /*
             * Create a sequence of instructions, with the first being the literal array assigned to
             * the temp.
             */

            final RubyNode splatCastNode = SplatCastNodeGen.create(translatingNextExpression ? SplatCastNode.NilBehavior.EMPTY_ARRAY : SplatCastNode.NilBehavior.ARRAY_WITH_NIL, true, environment.findLocalVarNode(tempRHSName, source, sourceSection));
            splatCastNode.unsafeSetSourceSection(sourceSection);

            final RubyNode writeTemp = environment.findLocalVarNode(tempName, source, sourceSection).makeWriteNode(splatCastNode);

            sequence.add(writeTemp);

            /*
             * Then index the temp array for each assignment on the LHS.
             */

            for (int n = 0; n < preArray.size(); n++) {
                final RubyNode assignedValue = PrimitiveArrayNodeFactory.read(environment.findLocalVarNode(tempName, source, sourceSection), n);

                sequence.add(translateDummyAssignment(preArray.get(n), assignedValue));
            }

            if (node.getRest() != null) {
                RubyNode assignedValue = ArrayGetTailNodeGen.create(preArray.size(), environment.findLocalVarNode(tempName, source, sourceSection));

                if (postArray != null) {
                    assignedValue = ArrayDropTailNodeGen.create(postArray.size(), assignedValue);
                }

                sequence.add(translateDummyAssignment(node.getRest(), assignedValue));
            }

            if (postArray != null) {
                final List<RubyNode> smallerSequence = new ArrayList<>();

                for (int n = 0; n < postArray.size(); n++) {
                    final RubyNode assignedValue = PrimitiveArrayNodeFactory.read(environment.findLocalVarNode(tempName, source, sourceSection), node.getPreCount() + n);
                    smallerSequence.add(translateDummyAssignment(postArray.get(n), assignedValue));
                }

                final RubyNode smaller = sequence(sourceSection, smallerSequence);

                final List<RubyNode> atLeastAsLargeSequence = new ArrayList<>();

                for (int n = 0; n < postArray.size(); n++) {
                    final RubyNode assignedValue = PrimitiveArrayNodeFactory.read(environment.findLocalVarNode(tempName, source, sourceSection), -(postArray.size() - n));

                    atLeastAsLargeSequence.add(translateDummyAssignment(postArray.get(n), assignedValue));
                }

                final RubyNode atLeastAsLarge = sequence(sourceSection, atLeastAsLargeSequence);

                final RubyNode assignPost =
                        new IfElseNode(
                                new ArrayIsAtLeastAsLargeAsNode(node.getPreCount() + node.getPostCount(), environment.findLocalVarNode(tempName, source, sourceSection)),
                                atLeastAsLarge,
                                smaller);

                sequence.add(assignPost);
            }

            result = new ElidableResultNode(sequence(sourceSection, sequence), environment.findLocalVarNode(tempRHSName, source, sourceSection));
        } else if (node.getPre() == null
                && node.getPost() == null
                && node.getRest() instanceof StarParseNode) {
            result = rhsTranslated;
        } else if (node.getPre() == null
                && node.getPost() == null
                && node.getRest() != null
                && rhs != null
                && !(rhs instanceof ArrayParseNode)) {
            /*
             * *a = b
             *
             * >= 1.8, this seems to be the same as:
             *
             * a = *b
             */

            final List<RubyNode> sequence = new ArrayList<>();

            SplatCastNode.NilBehavior nilBehavior;

            if (translatingNextExpression) {
                nilBehavior = SplatCastNode.NilBehavior.EMPTY_ARRAY;
            } else {
                if (rhsTranslated instanceof SplatCastNode && ((SplatCastNodeGen) rhsTranslated).getChild() instanceof NilLiteralNode) {
                    rhsTranslated = ((SplatCastNodeGen) rhsTranslated).getChild();
                    nilBehavior = SplatCastNode.NilBehavior.CONVERT;
                } else {
                    nilBehavior = SplatCastNode.NilBehavior.ARRAY_WITH_NIL;
                }
            }

            final String tempRHSName = environment.allocateLocalTemp("rhs");
            final RubyNode writeTempRHS = environment.findLocalVarNode(tempRHSName, source, sourceSection).makeWriteNode(rhsTranslated);
            sequence.add(writeTempRHS);

            final SplatCastNode rhsSplatCast = SplatCastNodeGen.create(
                    nilBehavior,
                    true, environment.findLocalVarNode(tempRHSName, source, sourceSection));

            rhsSplatCast.unsafeSetSourceSection(sourceSection);

            final String tempRHSSplattedName = environment.allocateLocalTemp("rhs");
            final RubyNode writeTempSplattedRHS = environment.findLocalVarNode(tempRHSSplattedName, source, sourceSection).makeWriteNode(rhsSplatCast);
            sequence.add(writeTempSplattedRHS);

            sequence.add(translateDummyAssignment(node.getRest(), environment.findLocalVarNode(tempRHSSplattedName, source, sourceSection)));

            final RubyNode assignmentResult;

            if (nilBehavior == SplatCastNode.NilBehavior.CONVERT) {
                assignmentResult = environment.findLocalVarNode(tempRHSSplattedName, source, sourceSection);
            } else {
                assignmentResult = environment.findLocalVarNode(tempRHSName, source, sourceSection);
            }

            result = new ElidableResultNode(sequence(sourceSection, sequence), assignmentResult);
        } else if (node.getPre() == null
                && node.getPost() == null
                && node.getRest() != null
                && rhs != null
                && rhs instanceof ArrayParseNode) {
            /*
             * *a = [b, c]
             *
             * This seems to be the same as:
             *
             * a = [b, c]
             */
            result = translateDummyAssignment(node.getRest(), rhsTranslated);
        } else if (node.getPre() == null && node.getRest() != null && node.getPost() != null) {
            /*
             * Something like
             *
             *     *a,b = [1, 2, 3, 4]
             */

            // This is very similar to the case with pre and rest, so unify with that

            final List<RubyNode> sequence = new ArrayList<>();

            final String tempRHSName = environment.allocateLocalTemp("rhs");
            final RubyNode writeTempRHS = environment.findLocalVarNode(tempRHSName, source, sourceSection).makeWriteNode(rhsTranslated);
            sequence.add(writeTempRHS);

            /*
             * Create a temp for the array.
             */

            final String tempName = environment.allocateLocalTemp("array");

            /*
             * Create a sequence of instructions, with the first being the literal array assigned to
             * the temp.
             */


            final RubyNode splatCastNode = SplatCastNodeGen.create(translatingNextExpression ? SplatCastNode.NilBehavior.EMPTY_ARRAY : SplatCastNode.NilBehavior.ARRAY_WITH_NIL, false, environment.findLocalVarNode(tempRHSName, source, sourceSection));
            splatCastNode.unsafeSetSourceSection(sourceSection);

            final RubyNode writeTemp = environment.findLocalVarNode(tempName, source, sourceSection).makeWriteNode(splatCastNode);

            sequence.add(writeTemp);

            /*
             * Then index the temp array for each assignment on the LHS.
             */

            if (node.getRest() != null) {
                final ArrayDropTailNode assignedValue = ArrayDropTailNodeGen.create(postArray.size(), environment.findLocalVarNode(tempName, source, sourceSection));

                sequence.add(translateDummyAssignment(node.getRest(), assignedValue));
            }

            final List<RubyNode> smallerSequence = new ArrayList<>();

            for (int n = 0; n < postArray.size(); n++) {
                final RubyNode assignedValue = PrimitiveArrayNodeFactory.read(environment.findLocalVarNode(tempName, source, sourceSection), node.getPreCount() + n);
                smallerSequence.add(translateDummyAssignment(postArray.get(n), assignedValue));
            }

            final RubyNode smaller = sequence(sourceSection, smallerSequence);

            final List<RubyNode> atLeastAsLargeSequence = new ArrayList<>();

            for (int n = 0; n < postArray.size(); n++) {
                final RubyNode assignedValue = PrimitiveArrayNodeFactory.read(environment.findLocalVarNode(tempName, source, sourceSection), -(postArray.size() - n));

                atLeastAsLargeSequence.add(translateDummyAssignment(postArray.get(n), assignedValue));
            }

            final RubyNode atLeastAsLarge = sequence(sourceSection, atLeastAsLargeSequence);

            final RubyNode assignPost =
                    new IfElseNode(
                    new ArrayIsAtLeastAsLargeAsNode(node.getPreCount() + node.getPostCount(), environment.findLocalVarNode(tempName, source, sourceSection)),
                            atLeastAsLarge,
                            smaller);

            sequence.add(assignPost);

            result = new ElidableResultNode(sequence(sourceSection, sequence), environment.findLocalVarNode(tempRHSName, source, sourceSection));
        } else {
            throw new UnsupportedOperationException();
        }

        final RubyNode ret = new DefinedWrapperNode(context.getCoreStrings().ASSIGNMENT, result);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitNextNode(NextParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        if (!environment.isBlock() && !translatingWhile) {
            throw new RaiseException(context.getCoreExceptions().syntaxError("Invalid next", currentNode));
        }

        final RubyNode resultNode;

        final boolean t = translatingNextExpression;
        translatingNextExpression = true;
        try {
            resultNode = translateNodeOrNil(sourceSection, node.getValueNode());
        } finally {
            translatingNextExpression = t;
        }

        final RubyNode ret = new NextNode(resultNode);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitNilNode(NilParseNode node) {
        if (node instanceof NilImplicitParseNode) {
            final RubyNode ret = new NilLiteralNode(true);
            ret.unsafeSetSourceSection(node.getPosition());
            return addNewlineIfNeeded(node, ret);
        }

        if (node.getPosition() == null) {
            final RubyNode ret = new DeadNode(new Exception());
            return addNewlineIfNeeded(node, ret);
        }

        SourceIndexLength sourceSection = node.getPosition();
        final RubyNode ret = nilNode(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitNthRefNode(NthRefParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final ReadLocalNode readNode = environment.findFrameLocalGlobalVarNode("$~", source, sourceSection);
        final GetFromThreadAndFrameLocalStorageNode readMatchNode = new GetFromThreadAndFrameLocalStorageNode(readNode);
        final RubyNode ret = new ReadMatchReferenceNode(readMatchNode, node.getMatchNumber());
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitOpAsgnAndNode(OpAsgnAndParseNode node) {
        return translateOpAsgnAndNode(node, node.getFirstNode().accept(this), node.getSecondNode().accept(this));
    }

    private RubyNode translateOpAsgnAndNode(ParseNode node, RubyNode lhs, RubyNode rhs) {
        /*
         * This doesn't translate as you might expect!
         *
         * http://www.rubyinside.com/what-rubys-double-pipe-or-equals-really-does-5488.html
         */

        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode andNode = new AndNode(lhs, rhs);
        andNode.unsafeSetSourceSection(sourceSection);

        final RubyNode ret = new DefinedWrapperNode(context.getCoreStrings().ASSIGNMENT, andNode);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitOpAsgnConstDeclNode(OpAsgnConstDeclParseNode node) {
        RubyNode lhs = node.getFirstNode().accept(this);
        RubyNode rhs = node.getSecondNode().accept(this);

        if (!(rhs instanceof WriteConstantNode)) {
            rhs = ((ReadConstantNode) lhs).makeWriteNode(rhs);
        }

        switch (node.getOperator()) {
            case "&&": {
                return translateOpAsgnAndNode(node, lhs, rhs);
            }

            case "||": {
                final RubyNode defined = new DefinedNode(lhs);
                lhs = new AndNode(defined, lhs);
                return translateOpAsgOrNode(node, lhs, rhs);
            }

            default: {
                final SourceIndexLength sourceSection = node.getPosition();
                final RubyCallNodeParameters callParameters = new RubyCallNodeParameters(lhs, node.getOperator(), null, new RubyNode[] { rhs }, false, true);
                final RubyNode opNode = context.getCoreMethods().createCallNode(callParameters);
                final RubyNode ret = ((ReadConstantNode) lhs).makeWriteNode(opNode);
                ret.unsafeSetSourceSection(sourceSection);
                return addNewlineIfNeeded(node, ret);
            }
        }
    }

    @Override
    public RubyNode visitOpAsgnNode(OpAsgnParseNode node) {
        final SourceIndexLength pos = node.getPosition();

        final boolean isOrOperator = node.getOperatorName().equals("||");
        if (isOrOperator || node.getOperatorName().equals("&&")) {
            // Why does this ||= or &&= come through as a visitOpAsgnNode and not a visitOpAsgnOrNode?

            final String temp = environment.allocateLocalTemp("opassign");
            final ParseNode writeReceiverToTemp = new LocalAsgnParseNode(pos, temp, 0, node.getReceiverNode());
            final ParseNode readReceiverFromTemp = new LocalVarParseNode(pos, 0, temp);

            final ParseNode readMethod = new CallParseNode(pos, readReceiverFromTemp, node.getVariableName(), null, null);
            final ParseNode writeMethod = new CallParseNode(pos, readReceiverFromTemp, node.getVariableName() + "=", buildArrayNode(pos,
                    node.getValueNode()), null);

            final SourceIndexLength sourceSection = pos;

            RubyNode lhs = readMethod.accept(this);
            RubyNode rhs = writeMethod.accept(this);

            final RubyNode controlNode = isOrOperator ? new OrNode(lhs, rhs) : new AndNode(lhs, rhs);
            final RubyNode ret = new DefinedWrapperNode(
                    context.getCoreStrings().ASSIGNMENT,
                    sequence(
                            sourceSection,
                            Arrays.asList(writeReceiverToTemp.accept(this), controlNode)));
            ret.unsafeSetSourceSection(sourceSection);
            return addNewlineIfNeeded(node, ret);
        }

        /*
         * We're going to de-sugar a.foo += c into a.foo = a.foo + c. Note that we can't evaluate a
         * more than once, so we put it into a temporary, and we're doing something more like:
         *
         * temp = a; temp.foo = temp.foo + c
         */

        final String temp = environment.allocateLocalTemp("opassign");
        final ParseNode writeReceiverToTemp = new LocalAsgnParseNode(pos, temp, 0, node.getReceiverNode());

        final ParseNode readReceiverFromTemp = new LocalVarParseNode(pos, 0, temp);

        final ParseNode readMethod = new CallParseNode(pos, readReceiverFromTemp, node.getVariableName(), null, null);
        final ParseNode operation = new CallParseNode(pos, readMethod, node.getOperatorName(),
                buildArrayNode(pos, node.getValueNode()), null);
        final ParseNode writeMethod = new CallParseNode(pos, readReceiverFromTemp, node.getVariableName() + "=",
                buildArrayNode(pos, operation), null);

        final BlockParseNode block = new BlockParseNode(pos);
        block.add(writeReceiverToTemp);

        final RubyNode writeTemp = writeReceiverToTemp.accept(this);
        RubyNode body = writeMethod.accept(this);

        final SourceIndexLength sourceSection = pos;

        if (node.isLazy()) {
            ReadLocalNode readLocal = environment.findLocalVarNode(temp, source, sourceSection);
            body = new IfNode(
                    new NotNode(new IsNilNode(readLocal)),
                    body);
            body.unsafeSetSourceSection(sourceSection);
        }
        final RubyNode ret = sequence(sourceSection, Arrays.asList(writeTemp, body));

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitOpAsgnOrNode(OpAsgnOrParseNode node) {
        RubyNode lhs = node.getFirstNode().accept(this);
        RubyNode rhs = node.getSecondNode().accept(this);

        // This is needed for class variables. Constants are handled separately in visitOpAsgnConstDeclNode.
        if (node.getFirstNode().needsDefinitionCheck() && !(node.getFirstNode() instanceof InstVarParseNode)) {
            RubyNode defined = new DefinedNode(lhs);
            lhs = new AndNode(defined, lhs);
        }

        return translateOpAsgOrNode(node, lhs, rhs);
    }

    private RubyNode translateOpAsgOrNode(ParseNode node, RubyNode lhs, RubyNode rhs) {
        /*
         * This doesn't translate as you might expect!
         *
         * http://www.rubyinside.com/what-rubys-double-pipe-or-equals-really-does-5488.html
         */

        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode ret = new DefinedWrapperNode(context.getCoreStrings().ASSIGNMENT,
                new OrNode(lhs, rhs));
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitOpElementAsgnNode(OpElementAsgnParseNode node) {
        /*
         * We're going to de-sugar a[b] += c into a[b] = a[b] + c. See discussion in
         * visitOpAsgnNode.
         */

        ParseNode index;

        if (node.getArgsNode() == null) {
            index = null;
        } else {
            index = node.getArgsNode().childNodes().get(0);
        }

        final ParseNode operand = node.getValueNode();

        final String temp = environment.allocateLocalTemp("opelementassign");
        final ParseNode writeArrayToTemp = new LocalAsgnParseNode(node.getPosition(), temp, 0, node.getReceiverNode());
        final ParseNode readArrayFromTemp = new LocalVarParseNode(node.getPosition(), 0, temp);

        final ParseNode arrayRead = new CallParseNode(node.getPosition(), readArrayFromTemp, "[]", buildArrayNode(node.getPosition(), index), null);

        final String op = node.getOperatorName();

        ParseNode operation = null;

        switch (op) {
            case "||":
                operation = new OrParseNode(node.getPosition(), arrayRead, operand);
                break;
            case "&&":
                operation = new AndParseNode(node.getPosition(), arrayRead, operand);
                break;
            default:
                operation = new CallParseNode(node.getPosition(), arrayRead, node.getOperatorName(), buildArrayNode(node.getPosition(), operand), null);
                break;
        }

        copyNewline(node, operation);

        final ParseNode arrayWrite = new CallParseNode(node.getPosition(), readArrayFromTemp, "[]=", buildArrayNode(node.getPosition(), index, operation), null);

        final BlockParseNode block = new BlockParseNode(node.getPosition());
        block.add(writeArrayToTemp);
        block.add(arrayWrite);

        final RubyNode ret = block.accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    private static ArrayParseNode buildArrayNode(SourceIndexLength sourcePosition, ParseNode first, ParseNode... rest) {
        if (first == null) {
            return new ArrayParseNode(sourcePosition);
        }

        final ArrayParseNode array = new ArrayParseNode(sourcePosition, first);

        for (ParseNode node : rest) {
            array.add(node);
        }

        return array;
    }

    @Override
    public RubyNode visitOrNode(OrParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode x = translateNodeOrNil(sourceSection, node.getFirstNode());
        final RubyNode y = translateNodeOrNil(sourceSection, node.getSecondNode());

        final RubyNode ret = new OrNode(x, y);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitPreExeNode(PreExeParseNode node) {
        // The parser seems to visit BEGIN blocks for us first, so we just need to translate them in place
        final RubyNode ret = node.getBodyNode().accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitPostExeNode(PostExeParseNode node) {
        // END blocks run after any other code - not just code in the same file

        // Turn into a call to Truffle::KernelOperations.at_exit

        // The scope is empty - we won't be able to access local variables
        // TODO fix this
        // https://github.com/jruby/jruby/issues/4257
        final StaticScope scope = new StaticScope(StaticScope.Type.BLOCK, null);

        return translateCallNode(
                new CallParseNode(node.getPosition(),
                        new TruffleFragmentParseNode(node.getPosition(), new ObjectLiteralNode(context.getCoreLibrary().getTruffleKernelOperationsModule())),
                        "at_exit",
                        new ListParseNode(node.getPosition(), new TrueParseNode(node.getPosition())),
                        new IterParseNode(node.getPosition(), node.getArgsNode(), scope, node.getBodyNode())),
                false, false, false);
    }

    @Override
    public RubyNode visitRationalNode(RationalParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        // TODO(CS): use IntFixnumLiteralNode where possible

        final RubyNode ret = translateRationalComplex(sourceSection, "Rational",
                new LongFixnumLiteralNode(node.getNumerator()),
                new LongFixnumLiteralNode(node.getDenominator()));

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitBigRationalNode(BigRationalParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode ret = translateRationalComplex(sourceSection, "Rational",
                bignumOrFixnumNode(node.getNumerator()),
                bignumOrFixnumNode(node.getDenominator()));

        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode translateRationalComplex(SourceIndexLength sourceSection, String name, RubyNode a, RubyNode b) {
        // Translate as Truffle.privately { Rational.convert(a, b) }

        final RubyNode moduleNode = new ObjectLiteralNode(context.getCoreLibrary().getObjectClass());
        ReadConstantNode receiver = new ReadConstantNode(moduleNode, name);
        RubyNode[] arguments = new RubyNode[] { a, b };
        RubyCallNodeParameters parameters = new RubyCallNodeParameters(receiver, "convert", null, arguments, false, true);
        return withSourceSection(sourceSection, new RubyCallNode(parameters));
    }

    @Override
    public RubyNode visitRedoNode(RedoParseNode node) {
        if (!environment.isBlock() && !translatingWhile) {
            throw new RaiseException(context.getCoreExceptions().syntaxError("Invalid redo", currentNode));
        }

        final RubyNode ret = new RedoNode();
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitRegexpNode(RegexpParseNode node) {
        final Rope rope = node.getValue();
        final RegexpOptions options = node.getOptions();
        options.setLiteral(true);
        Regex regex = RegexpNodes.compile(currentNode, context, rope, options);

        // The RegexpNodes.compile operation may modify the encoding of the source rope. This modified copy is stored
        // in the Regex object as the "user object". Since ropes are immutable, we need to take this updated copy when
        // constructing the final regexp.
        final Rope updatedRope = (Rope) regex.getUserObject();
        final DynamicObject regexp = RegexpNodes.createRubyRegexp(context.getCoreLibrary().getRegexpFactory(), regex, updatedRope, options);

        final ObjectLiteralNode literalNode = new ObjectLiteralNode(regexp);
        literalNode.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, literalNode);
    }

    public static boolean all7Bit(byte[] bytes) {
        for (int n = 0; n < bytes.length; n++) {
            if (bytes[n] < 0) {
                return false;
            }

            if (bytes[n] == '\\' && n + 1 < bytes.length && bytes[n + 1] == 'x') {
                final String num;
                final boolean isSecondHex = n + 3 < bytes.length && Character.digit(bytes[n + 3], 16) != -1;
                if (isSecondHex) {
                    num = new String(Arrays.copyOfRange(bytes, n + 2, n + 4), StandardCharsets.UTF_8);
                } else {
                    num = new String(Arrays.copyOfRange(bytes, n + 2, n + 3), StandardCharsets.UTF_8);
                }

                int b = Integer.parseInt(num, 16);

                if (b > 0x7F) {
                    return false;
                }

                if (isSecondHex) {
                    n += 3;
                } else {
                    n += 2;
                }

            }
        }

        return true;
    }

    @Override
    public RubyNode visitRescueNode(RescueParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        RubyNode tryPart;

        if (node.getBodyNode() == null || node.getBodyNode().getPosition() == null) {
            tryPart = nilNode(sourceSection);
        } else {
            tryPart = node.getBodyNode().accept(this);
        }

        final List<RescueNode> rescueNodes = new ArrayList<>();

        RescueBodyParseNode rescueBody = node.getRescueNode();

        if (context.getOptions().BACKTRACES_OMIT_UNUSED
                && rescueBody != null
                && rescueBody.getExceptionNodes() == null
                && rescueBody.getBodyNode() instanceof SideEffectFree
                // allow `expression rescue $!` pattern
                && (!(rescueBody.getBodyNode() instanceof GlobalVarParseNode) || !((GlobalVarParseNode) rescueBody.getBodyNode()).getName().equals("$!"))
                && rescueBody.getOptRescueNode() == null) {
            tryPart = new DisablingBacktracesNode(tryPart);

            RubyNode bodyNode;

            if (rescueBody.getBodyNode() == null || rescueBody.getBodyNode().getPosition() == null) {
                bodyNode = nilNode(sourceSection);
            } else {
                bodyNode = rescueBody.getBodyNode().accept(this);
            }

            final RescueAnyNode rescueNode = new RescueAnyNode(bodyNode);
            rescueNodes.add(rescueNode);
        } else {
            while (rescueBody != null) {
                if (rescueBody.getExceptionNodes() != null) {
                    final Deque<ParseNode> exceptionNodes = new ArrayDeque<>();
                    exceptionNodes.push(rescueBody.getExceptionNodes());

                    while (! exceptionNodes.isEmpty()) {
                        final ParseNode exceptionNode = exceptionNodes.pop();

                        if (exceptionNode instanceof ArrayParseNode) {
                            final RescueNode rescueNode = translateRescueArrayParseNode((ArrayParseNode) exceptionNode, rescueBody, sourceSection);
                            rescueNodes.add(rescueNode);
                        } else if (exceptionNode instanceof SplatParseNode) {
                            final RescueNode rescueNode = translateRescueSplatParseNode((SplatParseNode) exceptionNode, rescueBody, sourceSection);
                            rescueNodes.add(rescueNode);
                        } else if (exceptionNode instanceof ArgsCatParseNode) {
                            final ArgsCatParseNode argsCat = (ArgsCatParseNode) exceptionNode;
                            exceptionNodes.push(new SplatParseNode(argsCat.getSecondNode().getPosition(), argsCat.getSecondNode()));
                            exceptionNodes.push(argsCat.getFirstNode());
                        } else if (exceptionNode instanceof ArgsPushParseNode) {
                            final ArgsPushParseNode argsPush = (ArgsPushParseNode) exceptionNode;
                            exceptionNodes.push(new ArrayParseNode(argsPush.getSecondNode().getPosition(), argsPush.getSecondNode()));
                            exceptionNodes.push(argsPush.getFirstNode());
                        } else {
                            throw new UnsupportedOperationException();
                        }
                    }
                } else {
                    RubyNode bodyNode;

                    if (rescueBody.getBodyNode() == null || rescueBody.getBodyNode().getPosition() == null) {
                        bodyNode = nilNode(sourceSection);
                    } else {
                        bodyNode = rescueBody.getBodyNode().accept(this);
                    }

                    final RescueAnyNode rescueNode = new RescueAnyNode(bodyNode);
                    rescueNodes.add(rescueNode);
                }

                rescueBody = rescueBody.getOptRescueNode();
            }
        }

        RubyNode elsePart;

        if (node.getElseNode() == null || node.getElseNode().getPosition() == null) {
            elsePart = null; //nilNode(sourceSection);
        } else {
            elsePart = node.getElseNode().accept(this);
        }

        final RubyNode ret = new TryNode(
                new ExceptionTranslatingNode(tryPart, UnsupportedOperationBehavior.TYPE_ERROR),
                rescueNodes.toArray(new RescueNode[rescueNodes.size()]), elsePart);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    private RescueNode translateRescueArrayParseNode(ArrayParseNode arrayParse, RescueBodyParseNode rescueBody, SourceIndexLength sourceSection) {
        final ParseNode[] exceptionNodes = arrayParse.children();

        final RubyNode[] handlingClasses = new RubyNode[exceptionNodes.length];

        for (int n = 0; n < handlingClasses.length; n++) {
            handlingClasses[n] = exceptionNodes[n].accept(this);
        }

        RubyNode translatedBody;

        if (rescueBody.getBodyNode() == null || rescueBody.getBodyNode().getPosition() == null) {
            translatedBody = nilNode(sourceSection);
        } else {
            translatedBody = rescueBody.getBodyNode().accept(this);
        }

        return withSourceSection(sourceSection, new RescueClassesNode(handlingClasses, translatedBody));
    }

    private RescueNode translateRescueSplatParseNode(SplatParseNode splat, RescueBodyParseNode rescueBody, SourceIndexLength sourceSection) {
        final RubyNode splatTranslated = translateNodeOrNil(sourceSection, splat.getValue());

        RubyNode rescueBodyTranslated;

        if (rescueBody.getBodyNode() == null || rescueBody.getBodyNode().getPosition() == null) {
            rescueBodyTranslated = nilNode(sourceSection);
        } else {
            rescueBodyTranslated = rescueBody.getBodyNode().accept(this);
        }

        return withSourceSection(sourceSection, new RescueSplatNode(splatTranslated, rescueBodyTranslated));
    }

    @Override
    public RubyNode visitRetryNode(RetryParseNode node) {
        final RubyNode ret = new RetryNode();
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitReturnNode(ReturnParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        RubyNode translatedChild = translateNodeOrNil(sourceSection, node.getValueNode());

        final RubyNode ret = new ReturnNode(environment.getReturnID(), translatedChild);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitSClassNode(SClassParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode receiverNode = node.getReceiverNode().accept(this);
        final SingletonClassNode singletonClassNode = SingletonClassNodeGen.create(receiverNode);

        final boolean dynamicConstantLookup = environment.isDynamicConstantLookup();
        if (!dynamicConstantLookup) {
            if (environment.isModuleBody() && node.getReceiverNode() instanceof SelfParseNode) {
                // Common case of class << self in a module body, the constant lookup scope is still static
            } else if (environment.parent == null && environment.isModuleBody()) {
                // At the top-level of a file, opening the singleton class of a single expression
            } else {
                // Switch to dynamic constant lookup
                environment.getParseEnvironment().setDynamicConstantLookup(true);
                if (context.getOptions().LOG_DYNAMIC_CONSTANT_LOOKUP) {
                    Log.LOGGER.info(() -> "start dynamic constant lookup at " + RubyLanguage.fileLine(sourceSection.toSourceSection(source)));
                }
            }
        }

        final RubyNode ret;
        try {
            ret = openModule(sourceSection, singletonClassNode, "(singleton-def)", node.getBodyNode(), true);
        } finally {
            environment.getParseEnvironment().setDynamicConstantLookup(dynamicConstantLookup);
        }
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitSValueNode(SValueParseNode node) {
        final RubyNode ret = node.getValue().accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitSelfNode(SelfParseNode node) {
        final RubyNode ret = new SelfNode(environment.getFrameDescriptor());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitSplatNode(SplatParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode value = translateNodeOrNil(sourceSection, node.getValue());
        final RubyNode ret = SplatCastNodeGen.create(SplatCastNode.NilBehavior.CONVERT, false, value);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitStrNode(StrParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final Rope byteList = node.getValue();
        final CodeRange codeRange = node.getCodeRange();
        final Rope rope = context.getRopeTable().getRope(byteList, codeRange);

        final RubyNode ret;

        if (node.isFrozen() && !getSourcePath(sourceSection).startsWith(context.getCoreLibrary().getCoreLoadPath() + "/core/")) {
            final DynamicObject frozenString = context.getFrozenStrings().getFrozenString(rope);

            ret = new DefinedWrapperNode(context.getCoreStrings().METHOD,
                    new ObjectLiteralNode(frozenString));
        } else {
            ret = new StringLiteralNode(rope);
        }
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitSymbolNode(SymbolParseNode node) {
        final RubyNode ret = new ObjectLiteralNode(context.getSymbolTable().getSymbol(node.getRope()));
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitTrueNode(TrueParseNode node) {
        final RubyNode ret = new BooleanLiteralNode(true);
        ret.unsafeSetSourceSection(node.getPosition());

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitUndefNode(UndefParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();

        final RubyNode ret = ModuleNodesFactory.UndefMethodNodeFactory.create(new RubyNode[]{
                new RaiseIfFrozenNode(new GetDefaultDefineeNode()),
                ArrayToObjectArrayNodeGen.create(
                    ArrayLiteralNode.create(new RubyNode[]{
                        translateNameNodeToSymbol(node.getName())
                    })
                )
        });

        ret.unsafeSetSourceSection(sourceSection);

        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitUntilNode(UntilParseNode node) {
        WhileParseNode whileNode = new WhileParseNode(node.getPosition(), node.getConditionNode(), node.getBodyNode(), node.evaluateAtStart());
        copyNewline(node, whileNode);
        final RubyNode ret = translateWhileNode(whileNode, true);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitVCallNode(VCallParseNode node) {
        final SourceIndexLength sourceSection = node.getPosition();
        if (node.getName().equals("undefined") && getSourcePath(sourceSection).startsWith(corePath())) { // translate undefined
            final RubyNode ret = new ObjectLiteralNode(NotProvided.INSTANCE);
            ret.unsafeSetSourceSection(node.getPosition());
            return addNewlineIfNeeded(node, ret);
        }

        final ParseNode receiver = new SelfParseNode(node.getPosition());
        final CallParseNode callNode = new CallParseNode(node.getPosition(), receiver, node.getName(), null, null);
        copyNewline(node, callNode);
        final RubyNode ret = translateCallNode(callNode, true, true, false);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitWhileNode(WhileParseNode node) {
        final RubyNode ret = translateWhileNode(node, false);
        return addNewlineIfNeeded(node, ret);
    }

    private RubyNode translateWhileNode(WhileParseNode node, boolean conditionInversed) {
        final SourceIndexLength sourceSection = node.getPosition();

        RubyNode condition = node.getConditionNode().accept(this);
        if (conditionInversed) {
            condition = new NotNode(condition);
        }

        RubyNode body;
        final BreakID whileBreakID = environment.getParseEnvironment().allocateBreakID();

        final boolean oldTranslatingWhile = translatingWhile;
        translatingWhile = true;
        BreakID oldBreakID = environment.getBreakID();
        environment.setBreakIDForWhile(whileBreakID);
        frameOnStackMarkerSlotStack.push(BAD_FRAME_SLOT);
        try {
            body = translateNodeOrNil(sourceSection, node.getBodyNode());
        } finally {
            frameOnStackMarkerSlotStack.pop();
            environment.setBreakIDForWhile(oldBreakID);
            translatingWhile = oldTranslatingWhile;
        }

        final RubyNode loop;

        if (node.evaluateAtStart()) {
            loop = new WhileNode(new WhileNode.WhileRepeatingNode(context, condition, body));
        } else {
            loop = new WhileNode(new WhileNode.DoWhileRepeatingNode(context, condition, body));
        }

        final RubyNode ret = new CatchBreakNode(whileBreakID, loop);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitXStrNode(XStrParseNode node) {
        final ParseNode argsNode = buildArrayNode(node.getPosition(), new StrParseNode(node.getPosition(), node.getValue()));
        final ParseNode callNode = new FCallParseNode(node.getPosition(), "`", argsNode, null);
        final RubyNode ret = callNode.accept(this);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitYieldNode(YieldParseNode node) {
        final List<ParseNode> arguments = new ArrayList<>();

        ParseNode argsNode = node.getArgsNode();

        final boolean unsplat = argsNode instanceof SplatParseNode || argsNode instanceof ArgsCatParseNode;

        if (argsNode instanceof SplatParseNode) {
            argsNode = ((SplatParseNode) argsNode).getValue();
        }

        if (argsNode != null) {
            if (argsNode instanceof ListParseNode) {
                arguments.addAll((node.getArgsNode()).childNodes());
            } else {
                arguments.add(node.getArgsNode());
            }
        }

        final List<RubyNode> argumentsTranslated = new ArrayList<>();

        for (ParseNode argument : arguments) {
            argumentsTranslated.add(argument.accept(this));
        }

        final RubyNode[] argumentsTranslatedArray = argumentsTranslated.toArray(new RubyNode[argumentsTranslated.size()]);

        final RubyNode ret = new YieldExpressionNode(unsplat, argumentsTranslatedArray);
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitZArrayNode(ZArrayParseNode node) {
        final RubyNode[] values = new RubyNode[0];

        final RubyNode ret = ArrayLiteralNode.create(values);
        ret.unsafeSetSourceSection(node.getPosition());
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitBackRefNode(BackRefParseNode node) {
        int index = 0;

        switch (node.getType()) {
            case '`':
                index = ReadMatchReferenceNode.PRE;
                break;
            case '\'':
                index = ReadMatchReferenceNode.POST;
                break;
            case '&':
                index = ReadMatchReferenceNode.GLOBAL;
                break;
            case '+':
                index = ReadMatchReferenceNode.HIGHEST;
                break;
            default:
                throw new UnsupportedOperationException(Character.toString(node.getType()));
        }

        final SourceIndexLength sourceSection = node.getPosition();

        final ReadLocalNode readNode = environment.findFrameLocalGlobalVarNode("$~", source, sourceSection);
        final GetFromThreadAndFrameLocalStorageNode readMatchNode = new GetFromThreadAndFrameLocalStorageNode(readNode);
        final RubyNode ret = new ReadMatchReferenceNode(readMatchNode, index);
        ret.unsafeSetSourceSection(sourceSection);
        return addNewlineIfNeeded(node, ret);
    }

    @Override
    public RubyNode visitStarNode(StarParseNode star) {
        return nilNode(star.getPosition());
    }

    protected RubyNode initFlipFlopStates(SourceIndexLength sourceSection) {
        final RubyNode[] initNodes = new RubyNode[environment.getFlipFlopStates().size()];

        for (int n = 0; n < initNodes.length; n++) {
            initNodes[n] = new InitFlipFlopSlotNode(environment.getFlipFlopStates().get(n));
        }

        return sequence(sourceSection, Arrays.asList(initNodes));
    }

    @Override
    protected RubyNode defaultVisit(ParseNode node) {
        throw new UnsupportedOperationException(node.toString() + " " + node.getPosition());
    }

    public TranslatorEnvironment getEnvironment() {
        return environment;
    }

    protected String getIdentifierInNewEnvironment(boolean isBlock, String namedMethodName) {
        if (isBlock) {
            TranslatorEnvironment methodParent = environment;

            while (methodParent.isBlock()) {
                methodParent = methodParent.getParent();
            }

            if (environment.getBlockDepth() + 1 > 1) {
                return StringUtils.format("block (%d levels) in %s", environment.getBlockDepth() + 1, methodParent.getNamedMethodName());
            } else {
                return StringUtils.format("block in %s", methodParent.getNamedMethodName());
            }
        } else {
            return namedMethodName;
        }
    }

    @Override
    public RubyNode visitTruffleFragmentNode(TruffleFragmentParseNode node) {
        return addNewlineIfNeeded(node, node.getFragment());
    }

    @Override
    public RubyNode visitOther(ParseNode node) {
        if (node instanceof ReadLocalDummyParseNode) {
            final ReadLocalDummyParseNode readLocal = (ReadLocalDummyParseNode) node;
            final RubyNode ret = new ReadLocalVariableNode(LocalVariableType.FRAME_LOCAL, readLocal.getFrameSlot());
            ret.unsafeSetSourceSection(readLocal.getPosition());
            return addNewlineIfNeeded(node, ret);
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private void copyNewline(ParseNode from, ParseNode to) {
        if (from.isNewline()) {
            to.setNewline();
        }
    }

    private RubyNode addNewlineIfNeeded(ParseNode jrubyNode, RubyNode node) {
        if (jrubyNode.isNewline()) {
            final SourceIndexLength current = node.getEncapsulatingSourceIndexLength();

            if (current == null) {
                return node;
            }

            if (context.getCoverageManager().isEnabled()) {
                node.unsafeSetIsCoverageLine();
                context.getCoverageManager().setLineHasCode(source, current.toSourceSection(source).getStartLine());
            }

            node.unsafeSetIsNewLine();
        }

        return node;
    }

}
