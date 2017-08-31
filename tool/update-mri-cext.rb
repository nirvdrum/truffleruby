# Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
# This code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

# Run with ruby tool/update-mri-cext.rb

require 'fileutils'

TRUFFLERUBY_DIR = File.expand_path('../..', File.realpath(__FILE__))
TRUFFLERUBY_CEXT_LIB_DIR = File.join(TRUFFLERUBY_DIR, 'lib', 'cext')
RUBY_DIR = File.join(File.dirname(TRUFFLERUBY_DIR),'ruby')
RUBY_INCLUDE_DIR = File.join(RUBY_DIR, 'include')


raise "Cannot find ruby dir:`#{RUBY_DIR}`" unless File.exist?(RUBY_DIR)

Dir.glob("#{TRUFFLERUBY_CEXT_LIB_DIR}/**/*").each { |f| 
  FileUtils.rm_rf(f) unless (f.include?('preprocess.rb') || f.include?('truffle.h') || f.include?('internal.h') || f.end_with?('lib/cext/ruby') || f.include?('ruby/config.h') || f.end_with?('lib/cext/include') || f.include?('include/ruby.h') )
}

FileUtils.cp_r "#{RUBY_INCLUDE_DIR}/.", TRUFFLERUBY_CEXT_LIB_DIR

FileUtils::Verbose.cp_r "#{RUBY_DIR}/ccan/.", File.join(TRUFFLERUBY_CEXT_LIB_DIR, 'ccan')
Dir.glob(File.join(TRUFFLERUBY_CEXT_LIB_DIR, 'ccan','licenses/**')).each { |f| 
  FileUtils.rm_rf(f)
}

APPEND = <<-EOF
\\0

// Non-standard

NORETURN(void rb_tr_error(const char *message));
void rb_tr_log_warning(const char *message);
#define rb_tr_debug(args...) truffle_invoke(RUBY_CEXT, "rb_tr_debug", args)
long rb_tr_obj_id(VALUE object);
void rb_tr_hidden_variable_set(VALUE object, const char *name, VALUE value);
VALUE rb_tr_hidden_variable_get(VALUE object, const char *name);

// Handles

void *rb_tr_handle_for_managed(VALUE managed);
void *rb_tr_handle_for_managed_leaking(VALUE managed);
VALUE rb_tr_managed_from_handle_or_null(void *handle);
VALUE rb_tr_managed_from_handle(void *handle);
void rb_tr_release_handle(void *handle);

bool rb_tr_obj_taintable_p(VALUE object);
bool rb_tr_obj_tainted_p(VALUE object);
void rb_tr_obj_infect(VALUE a, VALUE b);

#define Qfalse_int_const 0
#define Qtrue_int_const 2
#define Qnil_int_const 4
int rb_tr_to_int_const(VALUE value);

int rb_tr_readable(int mode);
int rb_tr_writable(int mode);

typedef void *(*gvl_call)(void *);

#define RETURN_ENUMERATOR_NAME(obj, meth, argc, argv) do {      \
if (!rb_block_given_p())                                    \
    return rb_enumeratorize((obj), (meth), (argc), (argv)); \
} while (0)

// Memory

#define xmalloc                     ruby_xmalloc
#define xmalloc2                    ruby_xmalloc2
#define xcalloc                     ruby_xcalloc
#define xrealloc                    ruby_xrealloc
#define xfree                       ruby_xfree
// TODO CS 4-Mar-17 malloc and all these macros should be a version that throws an exception on failure
#define ruby_xmalloc                malloc
#define ruby_xmalloc2(items, size)  malloc((items)*(size))
#define ruby_xcalloc                calloc
#define ruby_xrealloc               realloc
#define ruby_xfree                  free

// Exceptions

#define rb_raise(EXCEPTION, FORMAT, ...) \
rb_exc_raise(rb_exc_new_str(EXCEPTION, (VALUE) truffle_invoke(rb_mKernel, "sprintf", rb_str_new_cstr(FORMAT), ##__VA_ARGS__)))

// Utilities

MUST_INLINE int rb_tr_scan_args(int argc, VALUE *argv, const char *format, VALUE *v1, VALUE *v2, VALUE *v3, VALUE *v4, VALUE *v5, VALUE *v6, VALUE *v7, VALUE *v8, VALUE *v9, VALUE *v10);

#define rb_tr_scan_args_1(ARGC, ARGV, FORMAT, V1) rb_tr_scan_args(ARGC, ARGV, FORMAT, V1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define rb_tr_scan_args_2(ARGC, ARGV, FORMAT, V1, V2) rb_tr_scan_args(ARGC, ARGV, FORMAT, V1, V2, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define rb_tr_scan_args_3(ARGC, ARGV, FORMAT, V1, V2, V3) rb_tr_scan_args(ARGC, ARGV, FORMAT, V1, V2, V3, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define rb_tr_scan_args_4(ARGC, ARGV, FORMAT, V1, V2, V3, V4) rb_tr_scan_args(ARGC, ARGV, FORMAT, V1, V2, V3, V4, NULL, NULL, NULL, NULL, NULL, NULL)
#define rb_tr_scan_args_5(ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5) rb_tr_scan_args(ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, NULL, NULL, NULL, NULL, NULL)
#define rb_tr_scan_args_6(ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6) rb_tr_scan_args(ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, NULL, NULL, NULL, NULL)
#define rb_tr_scan_args_7(ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7) rb_tr_scan_args(ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, NULL, NULL, NULL)
#define rb_tr_scan_args_8(ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8) rb_tr_scan_args(ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, NULL, NULL)
#define rb_tr_scan_args_9(ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, V9) rb_tr_scan_args(ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, V9, NULL)
#define rb_tr_scan_args_10(ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) rb_tr_scan_args(ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10)

#define SCAN_ARGS_IMPL(_1, _2, _3, _4, _5, _6, _7, _8, _9, _10, NAME, ...) NAME
#define rb_scan_args(ARGC, ARGV, FORMAT, ...) SCAN_ARGS_IMPL(__VA_ARGS__, rb_tr_scan_args_10, rb_tr_scan_args_9, rb_tr_scan_args_8, rb_tr_scan_args_7, rb_tr_scan_args_6, rb_tr_scan_args_5, rb_tr_scan_args_4, rb_tr_scan_args_3, rb_tr_scan_args_2, rb_tr_scan_args_1)(ARGC, ARGV, FORMAT, __VA_ARGS__)


// Calls 

#define rb_funcall(object, ...) truffle_invoke(RUBY_CEXT, "rb_funcall", (void *) object, __VA_ARGS__)

// Inline implementations

MUST_INLINE int rb_nativethread_lock_initialize(rb_nativethread_lock_t *lock) {
  *lock = truffle_invoke(RUBY_CEXT, "rb_nativethread_lock_initialize");
  return 0;
}

MUST_INLINE int rb_nativethread_lock_destroy(rb_nativethread_lock_t *lock) {
  *lock = NULL;
  return 0;
}

MUST_INLINE int rb_nativethread_lock_lock(rb_nativethread_lock_t *lock) {
  truffle_invoke(*lock, "lock");
  return 0;
}

MUST_INLINE int rb_nativethread_lock_unlock(rb_nativethread_lock_t *lock) {
  truffle_invoke(*lock, "unlock");
  return 0;
}

MUST_INLINE int rb_range_values(VALUE range, VALUE *begp, VALUE *endp, int *exclp) {
  if (rb_obj_is_kind_of(range, rb_cRange)) {
    *begp = (VALUE) truffle_invoke(range, "begin");
    *endp = (VALUE) truffle_invoke(range, "end");
    *exclp = (int) truffle_invoke_b(range, "exclude_end?");
  }
  else {
    if (!truffle_invoke_b(range, "respond_to?", rb_intern("begin"))) return (int)Qfalse;
    if (!truffle_invoke_b(range, "respond_to?", rb_intern("end"))) return (int)Qfalse;

    *begp = truffle_invoke(range, "begin");
    *endp = truffle_invoke(range, "end");
    *exclp = (int) RTEST(truffle_invoke(range, "exclude_end?"));
  }
  return (int) Qtrue;
}

MUST_INLINE VALUE rb_string_value(VALUE *value_pointer) {
  VALUE value = *value_pointer;

  if (!RB_TYPE_P(value, T_STRING)) {
    value = rb_str_to_str(value);
    *value_pointer = value;
  }

  return value;
}

MUST_INLINE char *rb_string_value_ptr(VALUE *value_pointer) {
  VALUE string = rb_string_value(value_pointer);
  return RSTRING_PTR(string);
}

MUST_INLINE char *rb_string_value_cstr(VALUE *value_pointer) {
  VALUE string = rb_string_value(value_pointer);

  truffle_invoke(RUBY_CEXT, "rb_string_value_cstr_check", string);

  return RSTRING_PTR(string);
}

MUST_INLINE int rb_tr_scan_args(int argc, VALUE *argv, const char *format, VALUE *v1, VALUE *v2, VALUE *v3, VALUE *v4, VALUE *v5, VALUE *v6, VALUE *v7, VALUE *v8, VALUE *v9, VALUE *v10) {
  // Parse the format string

  // TODO CS 7-Feb-17 maybe we could inline cache this part?

  const char *formatp = format;
  int pre = 0;
  int optional = 0;
  bool rest;
  int post = 0;
  bool kwargs;
  bool block;

  // TODO CS 27-Feb-17 can LLVM constant-fold through isdigit?

  if (isdigit(*formatp)) {
    pre = *formatp - '0';
    formatp++;

    if (isdigit(*formatp)) {
      optional = *formatp - '0';
      formatp++;
    }
  }

  if (*formatp == '*') {
    rest = true;
    formatp++;
  } else {
    rest = false;
  }

  if (isdigit(*formatp)) {
    post = *formatp - '0';
    formatp++;
  }

  if (*formatp == ':') {
    kwargs = true;
    formatp++;
  } else {
    kwargs = false;
  }

  if (*formatp == '&') {
    block = true;
    formatp++;
  } else {
    block = false;
  }

  if (*formatp != '\0') {
    rb_raise(rb_eArgError, "bad rb_scan_args format");
  }

  // Check we have enough arguments

  if (pre + post > argc) {
    rb_raise(rb_eArgError, "not enough arguments for required");
  }

  // Read arguments

  int argn = 0;
  int valuen = 1; // We've numbered the v parameters from 1
  bool taken_rest = false;
  bool taken_block = false;
  bool taken_kwargs = false;
  bool erased_kwargs = false;
  bool found_kwargs = false;

  if (rest && kwargs && !truffle_invoke_b(RUBY_CEXT, "test_kwargs", argv[argc - 1], Qfalse)) {
    kwargs = false;
    erased_kwargs = true;
  }

  int trailing = post;

  if (kwargs) {
    trailing++;
  }

  while (true) {
    // Get the next argument

    VALUE arg;

    if (pre > 0 || optional > 0) {
      if (argn < argc - trailing) {
        arg = argv[argn];
        argn++;
      } else {
        arg = Qnil;
      }

      if (pre > 0) {
        pre--;
      } else {
        optional--;
      }
    } else if (rest && !taken_rest) {
      arg = rb_ary_new();
      while (argn < argc - trailing) {
        rb_ary_push(arg, argv[argn]);
        argn++;
      }
      taken_rest = true;
    } else if (post > 0) {
      arg = argv[argn];
      argn++;
      post--;
    } else if (kwargs && !taken_kwargs) {
       if (argn < argc) {
        arg = argv[argn];
        truffle_invoke(RUBY_CEXT, "test_kwargs", argv[argn], Qtrue);
        argn++;
        found_kwargs = true;
      } else {
        arg = Qnil;
      }
      taken_kwargs = true;
    } else if (erased_kwargs && !taken_kwargs) {
      arg = Qnil;
      taken_kwargs = true;
    } else if (block && !taken_block) {
      if (rb_block_given_p()) {
        arg = rb_block_proc();
      } else {
        arg = Qnil;
      }
      taken_block = true;
    } else {
      break;
    }

    // Put the argument into the current value pointer

    // Don't assign the correct v to a temporary VALUE* and then assign arg to it - this doesn't optimise well

    switch (valuen) {
      case 1: *v1 = arg; break;
      case 2: *v2 = arg; break;
      case 3: *v3 = arg; break;
      case 4: *v4 = arg; break;
      case 5: *v5 = arg; break;
      case 6: *v6 = arg; break;
      case 7: *v7 = arg; break;
      case 8: *v8 = arg; break;
      case 9: *v9 = arg; break;
      case 10: *v10 = arg; break;
    }

    valuen++;
  }

  if (found_kwargs) {
    return argc - 1;
  } else {
    return argc;
  }
}

EOF


RB_TYPE_P = <<-EOF
bool RB_TYPE_P(VALUE value, int type);
EOF

AFTER_CONFIG = <<-EOF
\\0
#include <truffle.h>
#include <ctype.h> // isdigit

// Support

#define RUBY_CEXT (void *)truffle_import_cached("ruby_cext")
#define MUST_INLINE __attribute__((always_inline)) inline

#include <ruby/thread_native.h>

EOF

SPECIAL_CONST_P = <<-EOF
VALUE rb_special_const_p(VALUE object);
#define RB_SPECIAL_CONST_P(x) rb_special_const_p(x)
EOF

RB_ENCODING = <<-EOF
typedef struct {
  char *name;
} rb_encoding;
EOF

STR_WITH_ENC = <<-EOF
VALUE rb_external_str_with_enc(VALUE string, rb_encoding *eenc);
rb_encoding *get_encoding(VALUE string);
#define STR_ENC_GET(string) get_encoding(string)
\\0
EOF

RDATA = <<-EOF
\\0
struct RData *RDATA(VALUE value);
EOF

RB_IO = <<-EOF
typedef struct rb_io_t {
  int mode;
  int fd;
} rb_io_t;
EOF

GENERATED_CONSTANTS = <<-EOF
// START from tool/generate-cext-constants.rb

VALUE rb_tr_get_undef(void);
VALUE rb_tr_get_true(void);
VALUE rb_tr_get_false(void);
VALUE rb_tr_get_nil(void);
VALUE rb_tr_get_Array(void);
VALUE rb_tr_get_Bignum(void);
VALUE rb_tr_get_Class(void);
VALUE rb_tr_get_Comparable(void);
VALUE rb_tr_get_Data(void);
VALUE rb_tr_get_Encoding(void);
VALUE rb_tr_get_Enumerable(void);
VALUE rb_tr_get_FalseClass(void);
VALUE rb_tr_get_File(void);
VALUE rb_tr_get_Fixnum(void);
VALUE rb_tr_get_Float(void);
VALUE rb_tr_get_Hash(void);
VALUE rb_tr_get_Integer(void);
VALUE rb_tr_get_IO(void);
VALUE rb_tr_get_Kernel(void);
VALUE rb_tr_get_Match(void);
VALUE rb_tr_get_Module(void);
VALUE rb_tr_get_NilClass(void);
VALUE rb_tr_get_Numeric(void);
VALUE rb_tr_get_Object(void);
VALUE rb_tr_get_Range(void);
VALUE rb_tr_get_Regexp(void);
VALUE rb_tr_get_String(void);
VALUE rb_tr_get_Struct(void);
VALUE rb_tr_get_Symbol(void);
VALUE rb_tr_get_Time(void);
VALUE rb_tr_get_Thread(void);
VALUE rb_tr_get_TrueClass(void);
VALUE rb_tr_get_Proc(void);
VALUE rb_tr_get_Method(void);
VALUE rb_tr_get_Dir(void);
VALUE rb_tr_get_ArgError(void);
VALUE rb_tr_get_EOFError(void);
VALUE rb_tr_get_Errno(void);
VALUE rb_tr_get_Exception(void);
VALUE rb_tr_get_FloatDomainError(void);
VALUE rb_tr_get_IndexError(void);
VALUE rb_tr_get_Interrupt(void);
VALUE rb_tr_get_IOError(void);
VALUE rb_tr_get_LoadError(void);
VALUE rb_tr_get_LocalJumpError(void);
VALUE rb_tr_get_MathDomainError(void);
VALUE rb_tr_get_EncCompatError(void);
VALUE rb_tr_get_NameError(void);
VALUE rb_tr_get_NoMemError(void);
VALUE rb_tr_get_NoMethodError(void);
VALUE rb_tr_get_NotImpError(void);
VALUE rb_tr_get_RangeError(void);
VALUE rb_tr_get_RegexpError(void);
VALUE rb_tr_get_RuntimeError(void);
VALUE rb_tr_get_ScriptError(void);
VALUE rb_tr_get_SecurityError(void);
VALUE rb_tr_get_Signal(void);
VALUE rb_tr_get_StandardError(void);
VALUE rb_tr_get_SyntaxError(void);
VALUE rb_tr_get_SystemCallError(void);
VALUE rb_tr_get_SystemExit(void);
VALUE rb_tr_get_SysStackError(void);
VALUE rb_tr_get_TypeError(void);
VALUE rb_tr_get_ThreadError(void);
VALUE rb_tr_get_WaitReadable(void);
VALUE rb_tr_get_WaitWritable(void);
VALUE rb_tr_get_ZeroDivError(void);
VALUE rb_tr_get_stdin(void);
VALUE rb_tr_get_stdout(void);
VALUE rb_tr_get_stderr(void);
VALUE rb_tr_get_output_fs(void);
VALUE rb_tr_get_rs(void);
VALUE rb_tr_get_output_rs(void);
VALUE rb_tr_get_default_rs(void);

#define Qundef rb_tr_get_undef()
#define Qtrue rb_tr_get_true()
#define Qfalse rb_tr_get_false()
#define Qnil rb_tr_get_nil()
#define rb_cArray rb_tr_get_Array()
#define rb_cBignum rb_tr_get_Bignum()
#define rb_cClass rb_tr_get_Class()
#define rb_mComparable rb_tr_get_Comparable()
#define rb_cData rb_tr_get_Data()
#define rb_cEncoding rb_tr_get_Encoding()
#define rb_mEnumerable rb_tr_get_Enumerable()
#define rb_cFalseClass rb_tr_get_FalseClass()
#define rb_cFile rb_tr_get_File()
#define rb_cFixnum rb_tr_get_Fixnum()
#define rb_cFloat rb_tr_get_Float()
#define rb_cHash rb_tr_get_Hash()
#define rb_cInteger rb_tr_get_Integer()
#define rb_cIO rb_tr_get_IO()
#define rb_mKernel rb_tr_get_Kernel()
#define rb_cMatch rb_tr_get_Match()
#define rb_cModule rb_tr_get_Module()
#define rb_cNilClass rb_tr_get_NilClass()
#define rb_cNumeric rb_tr_get_Numeric()
#define rb_cObject rb_tr_get_Object()
#define rb_cRange rb_tr_get_Range()
#define rb_cRegexp rb_tr_get_Regexp()
#define rb_cString rb_tr_get_String()
#define rb_cStruct rb_tr_get_Struct()
#define rb_cSymbol rb_tr_get_Symbol()
#define rb_cTime rb_tr_get_Time()
#define rb_cThread rb_tr_get_Thread()
#define rb_cTrueClass rb_tr_get_TrueClass()
#define rb_cProc rb_tr_get_Proc()
#define rb_cMethod rb_tr_get_Method()
#define rb_cDir rb_tr_get_Dir()
#define rb_eArgError rb_tr_get_ArgError()
#define rb_eEOFError rb_tr_get_EOFError()
#define rb_mErrno rb_tr_get_Errno()
#define rb_eException rb_tr_get_Exception()
#define rb_eFloatDomainError rb_tr_get_FloatDomainError()
#define rb_eIndexError rb_tr_get_IndexError()
#define rb_eInterrupt rb_tr_get_Interrupt()
#define rb_eIOError rb_tr_get_IOError()
#define rb_eLoadError rb_tr_get_LoadError()
#define rb_eLocalJumpError rb_tr_get_LocalJumpError()
#define rb_eMathDomainError rb_tr_get_MathDomainError()
#define rb_eEncCompatError rb_tr_get_EncCompatError()
#define rb_eNameError rb_tr_get_NameError()
#define rb_eNoMemError rb_tr_get_NoMemError()
#define rb_eNoMethodError rb_tr_get_NoMethodError()
#define rb_eNotImpError rb_tr_get_NotImpError()
#define rb_eRangeError rb_tr_get_RangeError()
#define rb_eRegexpError rb_tr_get_RegexpError()
#define rb_eRuntimeError rb_tr_get_RuntimeError()
#define rb_eScriptError rb_tr_get_ScriptError()
#define rb_eSecurityError rb_tr_get_SecurityError()
#define rb_eSignal rb_tr_get_Signal()
#define rb_eStandardError rb_tr_get_StandardError()
#define rb_eSyntaxError rb_tr_get_SyntaxError()
#define rb_eSystemCallError rb_tr_get_SystemCallError()
#define rb_eSystemExit rb_tr_get_SystemExit()
#define rb_eSysStackError rb_tr_get_SysStackError()
#define rb_eTypeError rb_tr_get_TypeError()
#define rb_eThreadError rb_tr_get_ThreadError()
#define rb_mWaitReadable rb_tr_get_WaitReadable()
#define rb_mWaitWritable rb_tr_get_WaitWritable()
#define rb_eZeroDivError rb_tr_get_ZeroDivError()
#define rb_stdin rb_tr_get_stdin()
#define rb_stdout rb_tr_get_stdout()
#define rb_stderr rb_tr_get_stderr()
#define rb_output_fs rb_tr_get_output_fs()
#define rb_rs rb_tr_get_rs()
#define rb_output_rs rb_tr_get_output_rs()
#define rb_default_rs rb_tr_get_default_rs()

// END from tool/generate-cext-constants.rb
EOF

RB_NATIVE_THREAD = <<-EOF
typedef void *rb_nativethread_id_t;
typedef void *rb_nativethread_lock_t;
EOF

DEFINE_VALUE = <<-EOF
typedef void *VALUE;
typedef long SIGNED_VALUE;
typedef VALUE ID;
EOF

RSTRING_LEN = <<-EOF
int rb_str_len(VALUE string);
#define RSTRING_LEN(str) (long)rb_str_len(str)
EOF

RSTRING_PTR = <<-EOF
#define RSTRING_PTR(str) RSTRING_PTR_IMPL(str)
char *RSTRING_PTR_IMPL(VALUE string);
EOF

PATCH_SETS = [
    {:file => 'ruby/ruby.h',
     :patches => [
         {:match => /static inline int\nrb_type\(VALUE obj\).*?^}/m,
          :replacement => ''},
         {:match => /#define RB_TYPE_P\(obj, type\) \( \\.*?\)\)\)/m,
          :replacement => RB_TYPE_P},
         {:match => /#include "ruby\/config.h"/,
          :replacement => AFTER_CONFIG},
         {:match => /^RUBY_SYMBOL_EXPORT_END/,
          :replacement => APPEND},
         {:match => /void \*rb_alloc_tmp_buffer\(volatile VALUE \*store, long len\)/,
          :replacement => 'void *rb_alloc_tmp_buffer(VALUE *buffer_pointer, long length);'},
         {:match => /static inline int rb_type\(VALUE obj\);/,
          :replacement => 'int rb_type(VALUE value);'},
         {:match => /void rb_free_tmp_buffer\(volatile VALUE \*store\)/,
          :replacement => 'void rb_free_tmp_buffer(VALUE *store);'},
         {:match => /#define SYMBOL_P\(x\) RB_SYMBOL_P\(x\)/,
          :replacement => 'bool SYMBOL_P(VALUE value);'},
         {:match => /#define CHR2FIX\(x\) RB_CHR2FIX\(x\)/,
          :replacement => ''},
         {:match => /#define NUM2INT\(x\)  RB_NUM2INT\(x\)/,
          :replacement => 'int NUM2INT(VALUE value);'},
         {:match => /#define NUM2UINT\(x\) RB_NUM2UINT\(x\)/,
          :replacement => 'unsigned int NUM2UINT(VALUE value);'},
         {:match => /#define NUM2LONG\(x\) RB_NUM2LONG\(x\)/,
          :replacement => 'long NUM2LONG(VALUE value);'},
         {:match => /#define NUM2ULONG\(x\) RB_NUM2ULONG\(x\)/,
          :replacement => 'unsigned long NUM2ULONG(VALUE value);'},
         {:match => /#define NUM2DBL\(x\) rb_num2dbl\(\(VALUE\)\(x\)\)/,
          :replacement => 'double NUM2DBL(VALUE value);'},
         {:match => /#define FIX2INT\(x\)  RB_FIX2INT\(x\)/,
          :replacement => 'int FIX2INT(VALUE value);'},
         {:match => /#define FIX2UINT\(x\) RB_FIX2UINT\(x\)/,
          :replacement => ''},
         {:match => /#define FIX2LONG\(x\) RB_FIX2LONG\(x\)/,
          :replacement => 'long FIX2LONG(VALUE value);'},
         {:match => /#define FIX2ULONG\(x\) RB_FIX2ULONG\(x\)/,
          :replacement => ''},
         {:match => /#define INT2NUM\(x\) RB_INT2NUM\(x\)/,
          :replacement => 'VALUE INT2NUM(long value);'},
         {:match => /#define INT2FIX\(i\) \(\(\(VALUE\)\(i\)\)<<1 \| RUBY_FIXNUM_FLAG\)/,
          :replacement => 'VALUE INT2FIX(long value);'},
         {:match => /#define UINT2NUM\(x\) RB_UINT2NUM\(x\)/,
          :replacement => ''},
         {:match => /#define LONG2NUM\(x\) RB_LONG2NUM\(x\)/,
          :replacement => 'VALUE LONG2NUM(long value);'},
         {:match => /#define ULONG2NUM\(x\) RB_ULONG2NUM\(x\)/,
          :replacement => 'VALUE ULONG2NUM(unsigned long value);'},
         {:match => /#define LONG2FIX\(i\) INT2FIX\(i\)/,
          :replacement => 'VALUE LONG2FIX(long value);'},
         {:match => /#if SIZEOF_INT < SIZEOF_LONG\nstatic inline int\nrb_long2int_inline\(long n\).*?#endif/m,
          :replacement => 'int rb_long2int(long value);'},
         {:match => /#define RB_NUM2CHR\(x\) rb_num2char_inline\(x\)/,
          :replacement => 'char RB_NUM2CHR(VALUE x);'},
         {:match => /#define RB_FIXNUM_P\(f\) \(\(\(int\)\(SIGNED_VALUE\)\(f\)\)&RUBY_FIXNUM_FLAG\)/,
          :replacement => 'int RB_FIXNUM_P(VALUE value);'},
         {:match => /#define RTEST\(v\) !\(\(\(VALUE\)\(v\) & ~Qnil\) == 0\)/,
          :replacement => 'int RTEST(VALUE value);'},
         {:match => /#define RB_SPECIAL_CONST_P\(x\) \(RB_IMMEDIATE_P\(x\) \|\| !RTEST\(x\)\)/,
          :replacement => SPECIAL_CONST_P},
         {:match => /#ifdef __GNUC__\n#define rb_special_const_p\(obj\).*?#endif/m,
          :replacement => ''},
         {:match => /#define RSTRING_END\(str\).*?as.heap.len\)\)/m,
          :replacement => 'char *RSTRING_END(VALUE string);'},
         {:match => /static inline VALUE\nrb_data_typed_object_make\(.*?}/m,
          :replacement => 'VALUE rb_data_typed_object_make(VALUE ruby_class, const rb_data_type_t *type, void **data_pointer, size_t size);'},
         {:match => /#define rb_intern\(str\) \\.*?rb_intern\(str\)\)/m,
          :replacement => ''},
         {:match => /#define RARRAY_LEN\(a\) rb_array_len\(a\)/,
          :replacement => 'int RARRAY_LEN(VALUE array);'},
         {:match => /#define RARRAY_LENINT\(ary\) rb_long2int\(RARRAY_LEN\(ary\)\)/,
          :replacement => 'int RARRAY_LENINT(VALUE array);'},
         {:match => /#define RARRAY_AREF\(a, i\)    \(RARRAY_CONST_PTR\(a\)\[i\]\)/,
          :replacement => 'VALUE RARRAY_AREF(VALUE array, long index);'},
         {:match => /#define CLASS_OF\(v\) rb_class_of\(\(VALUE\)\(v\)\)/,
          :replacement => 'VALUE CLASS_OF(VALUE object);'},
         {:match => /static inline VALUE\nrb_class_of\(VALUE obj\).*?^}/m,
          :replacement => 'VALUE rb_class_of(VALUE object);'},
         {:match => /struct RData {.*?^};/m,
          :replacement => RDATA},
         {:match => /#define RDATA\(obj\)   \(R_CAST\(RData\)\(obj\)\)/,
          :replacement => ''},
         {:match => /void rb_define_const\(VALUE,const char\*,VALUE\);/,
          :replacement => 'VALUE rb_define_const(VALUE module, const char *name, VALUE value);'},
         {:match => /VALUE rb_gvar_var_getter\(ID id, void \*data, struct rb_global_variable \*gvar\);/,
          :replacement => 'VALUE rb_gvar_var_getter(ID id, VALUE *var, void *gvar);'},
         {:match => /void  rb_gvar_var_setter\(VALUE val, ID id, void \*data, struct rb_global_variable \*gvar\);/,
          :replacement => 'void rb_gvar_var_setter(VALUE val, ID id, VALUE *var, void *g);'},
         {:match => /void  rb_gvar_readonly_setter\(VALUE val, ID id, void \*data, struct rb_global_variable \*gvar\);/,
          :replacement => 'void rb_gvar_readonly_setter(VALUE v, ID id, void *d, void *g);'},
         {:match => /RUBY_EXTERN VALUE rb_mKernel;.*?RUBY_EXTERN VALUE rb_stdin, rb_stdout, rb_stderr;/m,
          :replacement => GENERATED_CONSTANTS},
         {:match => /VALUE rb_string_value\(volatile VALUE\*\);/,
          :replacement => 'MUST_INLINE VALUE rb_string_value(VALUE *value_pointer);'},
         {:match => /char \*rb_string_value_ptr\(volatile VALUE\*\);/,
          :replacement => 'MUST_INLINE char *rb_string_value_ptr(VALUE *value_pointer);'},
         {:match => /char \*rb_string_value_cstr\(volatile VALUE\*\);/,
          :replacement => 'MUST_INLINE char *rb_string_value_cstr(VALUE *value_pointer);'},
         {:match => /#if defined HAVE_UINTPTR_T && 0.*?#endif/m,
          :replacement => DEFINE_VALUE},
         {:match => /#define RSTRING_PTR\(str\).*?as.heap.ptr\)/m,
          :replacement => RSTRING_PTR},
         {:match => /#define RB_OBJ_TAINT\(x\) \(RB_OBJ_TAINTABLE\(x\) \? RB_OBJ_TAINT_RAW\(x\) : \(void\)0\)/,
          :replacement => '#define RB_OBJ_TAINT(object)            rb_obj_taint(object)'},
         {:match => /#define RSTRING_LEN\(str\).*?as.heap.len\)/m,
          :replacement => RSTRING_LEN},
         {:match => /#define RB_OBJ_FREEZE\(x\) rb_obj_freeze_inline\(\(VALUE\)x\)/,
          :replacement => '#define RB_OBJ_FREEZE(x)                rb_obj_freeze((VALUE)x)'},
         {:match => /static inline void\nrb_obj_freeze_inline\(.*?^}/m,
          :replacement => ''},
         {:match => /static inline void\nrb_clone_setup\(.*?^}/m,
          :replacement => ''},
         {:match => /static inline void\nrb_dup_setup\(.*?^}/m,
          :replacement => ''},
         {:match => /static inline long\nrb_array_len\(.*?^}/m,
          :replacement => ''},
         {:match => /static inline const VALUE \*\nrb_array_const_ptr\(.*?^}/m,
          :replacement => ''},
         {:match => /static inline long\nrb_struct_len\(.*?^}/m,
          :replacement => ''},
         {:match => /static inline const VALUE \*\nrb_struct_const_ptr\(.*?^}/m,
          :replacement => ''},
         {:match => /#define RSTRING_GETMEM\(.*?->as.heap.len\)\)/m,
          :replacement => '#define RSTRING_GETMEM(string, data_pointer, length_pointer) ((data_pointer) = RSTRING_PTR(string), (length_pointer) = rb_str_len(string))'},
         {:match => /#define RB_OBJ_FROZEN\(x\) \(!RB_FL_ABLE\(x\) \|\| RB_OBJ_FROZEN_RAW\(x\)\)/,
          :replacement => '#define RB_OBJ_FROZEN(x)                rb_obj_frozen_p(x)'},
         {:match => /#define RB_OBJ_INFECT\(.*?void\)0\)/m,
          :replacement => '#define RB_OBJ_INFECT(a, b)             rb_tr_obj_infect(a, b)'},
         {:match => /#define SafeStringValue\(.*?while \(0\)/m,
          :replacement => '#define SafeStringValue StringValue'}
     ]
    }, # end ruby.h
    {:file => 'ruby/intern.h',
     :patches => [
         {:match => /#ifdef __GNUC__\n#define rb_check_frozen\(obj\).*?#endif/m,
          :replacement => ''},
         {:match => /#define rb_str_new\(.*?}\)/m,
          :replacement => 'VALUE rb_str_new(const char *string, long length);'},
         {:match => /#define rb_str_new_cstr\(.*?}\)/m,
          :replacement => 'VALUE rb_str_new_cstr(const char *string);'},
         {:match => /#define rb_tainted_str_new_cstr\(.*?}\)/m,
          :replacement => 'VALUE rb_tainted_str_new_cstr(const char*);'},
         {:match => /#define rb_str_cat2 rb_str_cat_cstr/,
          :replacement => 'VALUE rb_str_cat2(VALUE string, const char *to_concat);'},
         {:match => /#define rb_str_buf_new_cstr\(.*?}\)/m,
          :replacement => 'VALUE rb_str_buf_new_cstr(const char *string);'},
         {:match => /#define rb_str_buf_cat rb_str_cat/,
          :replacement => 'VALUE rb_str_buf_cat(VALUE string, const char *to_concat, long length);'},
         {:match => /#define rb_external_str_new_cstr\(.*?}\)/m,
          :replacement => ''},
         {:match => /#define rb_locale_str_new_cstr\(str\).*?}\)/m,
          :replacement => ''},
         {:match => /#define rb_usascii_str_new\(.*?}\)/m,
          :replacement => ''},
         {:match => /#define rb_usascii_str_new_cstr\(.*?}\)/m,
          :replacement => ''},
         {:match => /static inline void\nrb_check_arity\(.*?^}/m,
          :replacement => 'void rb_check_arity(int argc, int min, int max);'},
         {:match => /#define rb_exc_new_cstr\(.*?^}\)/m,
          :replacement => ''},
         {:match => /VALUE rb_attr_get\(VALUE, ID\);/,
          :replacement => 'VALUE rb_attr_get(VALUE object, const char *name);'},
         {:match => /void rb_const_set\(VALUE, ID, VALUE\);/,
          :replacement => 'VALUE rb_const_set(VALUE module, ID name, VALUE value);'},
         {:match => /int rb_reg_options\(VALUE\);/,
          :replacement => 'VALUE rb_reg_options(VALUE re);'},
         {:match => /int rb_range_values\(VALUE range, VALUE \*begp, VALUE \*endp, int \*exclp\);/,
          :replacement => 'MUST_INLINE int rb_range_values(VALUE range, VALUE *begp, VALUE *endp, int *exclp);'}

     ]
    },
    {:file => 'ruby/encoding.h',
     :patches => [
         {:match => /typedef const OnigEncodingType rb_encoding;/,
          :replacement => RB_ENCODING},
         {:match => /#define MBCLEN_NEEDMORE_P\(ret\)      ONIGENC_MBCLEN_NEEDMORE_P\(ret\)/,
          :replacement => 'int MBCLEN_NEEDMORE_P(int r);'},
         {:match => /#define MBCLEN_NEEDMORE_LEN\(ret\)      ONIGENC_MBCLEN_NEEDMORE_LEN\(ret\)/,
          :replacement => 'int MBCLEN_NEEDMORE_LEN(int r);'},
         {:match => /#define MBCLEN_CHARFOUND_P\(ret\)     ONIGENC_MBCLEN_CHARFOUND_P\(ret\)/,
          :replacement => 'int MBCLEN_CHARFOUND_P(int r);'},
         {:match => /#define MBCLEN_CHARFOUND_LEN\(ret\)     ONIGENC_MBCLEN_CHARFOUND_LEN\(ret\)/,
          :replacement => 'int MBCLEN_CHARFOUND_LEN(int r);'},
         {:match => /VALUE rb_external_str_new_with_enc\(const char \*ptr, long len, rb_encoding \*\);/,
          :replacement => STR_WITH_ENC},
         {:match => /#define rb_enc_asciicompat\(enc\) \(rb_enc_mbminlen\(enc\)==1 && !rb_enc_dummy_p\(enc\)\)/,
          :replacement => ''},
         {:match => /#define RB_ENC_CODERANGE\(obj\) \(\(int\)RBASIC\(obj\)->flags & RUBY_ENC_CODERANGE_MASK\)/,
          :replacement => 'enum ruby_coderange_type RB_ENC_CODERANGE(VALUE obj);'},
         {:match => /#define rb_enc_str_new\(str.*?}\)/m,
          :replacement => ''},
         {:match => /#define rb_enc_left_char_head\(s,p,e,enc\) \(\(char \*\)onigenc_get_left_adjust_char_head\(\(enc\),\(UChar\*\)\(s\),\(UChar\*\)\(p\),\(UChar\*\)\(e\)\)\)/,
          :replacement => 'char* rb_enc_left_char_head(char *start, char *p, char *end, rb_encoding *enc);'},
         {:match => /#define rb_enc_mbmaxlen\(enc\) \(enc\)->max_enc_len/,
          :replacement => 'int rb_enc_mbmaxlen(rb_encoding *enc);'},
         {:match => /#define rb_enc_mbminlen\(enc\) \(enc\)->min_enc_len/,
          :replacement => 'int rb_enc_mbminlen(rb_encoding *enc);'}
     ]
    },
    {:file => 'ruby/io.h',
     :patches => [{:match => /typedef struct rb_io_t {.*?^} rb_io_t;/m,
                   :replacement => RB_IO
                  }]},
    {:file => 'ruby/thread_native.h',
     :patches => [{
                      :match => /typedef pthread_t rb_nativethread_id_t;.*?typedef pthread_mutex_t rb_nativethread_lock_t;/m,
                      :replacement => RB_NATIVE_THREAD
                  },
                  {:match => /void rb_nativethread_lock_initialize\(rb_nativethread_lock_t \*lock\);/,
                   :replacement => 'MUST_INLINE int rb_nativethread_lock_initialize(rb_nativethread_lock_t *lock);'},
                  {:match => /void rb_nativethread_lock_destroy\(rb_nativethread_lock_t \*lock\);/,
                   :replacement => 'MUST_INLINE int rb_nativethread_lock_destroy(rb_nativethread_lock_t *lock);'},
                  {:match => /void rb_nativethread_lock_lock\(rb_nativethread_lock_t \*lock\);/,
                   :replacement => 'MUST_INLINE int rb_nativethread_lock_lock(rb_nativethread_lock_t *lock);'},
                  {:match => /void rb_nativethread_lock_unlock\(rb_nativethread_lock_t \*lock\);/,
                   :replacement => 'MUST_INLINE int rb_nativethread_lock_unlock(rb_nativethread_lock_t *lock);'},
     ]}
]

PATCH_SETS.each do |patch_set|
  file = patch_set[:file]
  destfile = File.join(TRUFFLERUBY_CEXT_LIB_DIR, file)
  sourcefile = File.join(RUBY_INCLUDE_DIR, file)
  tempfile = File.open(destfile, 'w')
  f = File.new(sourcefile)
  contents = f.read
  patches = patch_set[:patches]
  patches.each do |patch|
    match = contents.match(patch[:match])
    puts "MATCH: #{match[0]} for patch:#{patch[:match]}"
    after = contents.gsub(patch[:match], patch[:replacement]) 
    raise "no change" if after == contents
    contents = after 
  end
  tempfile.write contents
  f.close
  tempfile.close
end

