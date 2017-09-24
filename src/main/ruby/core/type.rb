# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# Copyright (c) 2011, Evan Phoenix
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of the Evan Phoenix nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# The Type module provides facilities for accessing various "type" related
# data about an object, as well as providing type coercion methods. These
# facilities are independent of the object and thus are more robust in the
# face of ad hoc monkeypatching.

##
# Namespace for coercion functions between various ruby objects.

module Rubinius
  module Type

    def self.object_respond_to?(obj, name, include_private = false)
      Truffle.invoke_primitive :vm_object_respond_to, obj, name, include_private
    end

    def self.object_respond_to_no_built_in?(obj, name, include_private = false)
      meth = Truffle.invoke_primitive :vm_method_lookup, obj, name
      !meth.nil? && !Truffle.invoke_primitive(:vm_method_is_basic, meth)
    end

    def self.check_funcall_callable(obj, name)
      # TODO BJF Review rb_method_call_status
      Truffle.invoke_primitive :vm_method_lookup, obj, name
    end

    def self.object_encoding(obj)
      Truffle.primitive :encoding_get_object_encoding
      raise PrimitiveFailure, 'Rubinius::Type.object_encoding primitive failed'
    end

    ##
    # Returns an object of given class. If given object already is one, it is
    # returned. Otherwise tries obj.meth and returns the result if it is of the
    # right kind. TypeErrors are raised if the conversion method fails or the
    # conversion result is wrong.
    #
    # Uses Rubinius::Type.object_kind_of to bypass type check overrides.
    #
    # Equivalent to MRI's rb_convert_type().

    def self.coerce_to(obj, cls, meth)
      return obj if object_kind_of?(obj, cls)
      execute_coerce_to(obj, cls, meth)
    end

    def self.execute_coerce_to(obj, cls, meth)
      begin
        ret = obj.__send__(meth)
      rescue => orig
        coerce_to_failed obj, cls, meth, orig
      end

      return ret if object_kind_of?(ret, cls)

      coerce_to_type_error obj, ret, meth, cls
    end

    def self.coerce_to_failed(object, klass, method, exc=nil)
      if object_respond_to? object, :inspect
        raise TypeError,
            "Coercion error: #{object.inspect}.#{method} => #{klass} failed",
            exc
      else
        raise TypeError,
            "Coercion error: #{method} => #{klass} failed",
            exc
      end
    end

    def self.coerce_to_type_error(original, converted, method, klass)
      oc = object_class original
      cc = object_class converted
      msg = "failed to convert #{oc} to #{klass}: #{oc}\##{method} returned #{cc}"
      raise TypeError, msg
    end

    ##
    # Same as coerce_to but returns nil if conversion fails.
    # Corresponds to MRI's rb_check_convert_type()
    #
    def self.check_convert_type(obj, cls, meth)
      return obj if object_kind_of?(obj, cls)
      return nil unless object_respond_to?(obj, meth, true)
      execute_check_convert_type(obj, cls, meth)
    end

    def self.execute_check_convert_type(obj, cls, meth)
      begin
        ret = obj.__send__(meth)
      rescue
        return nil
      end

      return ret if ret.nil? || object_kind_of?(ret, cls)

      msg = "Coercion error: obj.#{meth} did NOT return a #{cls} (was #{object_class(ret)})"
      raise TypeError, msg
    end

    # MRI conversion macros and functions

    def self.rb_num2int(val)
      num = rb_num2long(val)
      check_int(num)
      num
    end

    def self.rb_num2uint(val)
      num = rb_num2long(val)
      check_uint(num)
      num
    end

    def self.rb_num2long(val)
      raise TypeError, 'no implicit conversion from nil to integer' if val.nil?

      if object_kind_of?(val, Fixnum)
        return val
      elsif object_kind_of?(val, Float)
        fval = val.to_int
        check_long(fval)
        return fval
      elsif object_kind_of?(val, Bignum)
        return rb_big2long(val)
      else
        return rb_num2long(rb_to_int(val))
      end
    end

    def self.rb_num2ulong(val)
      raise TypeError, 'no implicit conversion from nil to integer' if val.nil?

      if object_kind_of?(val, Fixnum)
        return val
      elsif object_kind_of?(val, Float)
        fval = val.to_int
        return rb_num2ulong(fval)
      elsif object_kind_of?(val, Bignum)
        return rb_big2ulong(val)
      else
        return rb_num2ulong(rb_to_int(val))
      end
    end

    def self.rb_num2dbl(val)
      raise TypeError, 'no implicit conversion from nil to float' if val.nil?

      if object_kind_of?(val, Float)
        return val
      elsif object_kind_of?(val, Fixnum)
        return val.to_f
      elsif object_kind_of?(val, Bignum)
        return val.to_f
      elsif object_kind_of?(val, String)
        raise TypeError, 'no implicit conversion from to float from string'
      else
        rb_num2dbl(rb_to_f(val))
      end
    end

    def self.rb_big2long(val)
      raise RangeError, "bignum too big to convert into `long'"
    end

    def self.rb_big2dbl(val)
      val.to_f
    end

    def self.rb_big2ulong(val)
      check_ulong(val)
      Truffle.invoke_primitive(:fixnum_ulong_from_bignum, val)
    end

    def self.rb_to_f(val)
      rb_to_float(val, :to_f);
    end

    def self.rb_to_int(val)
      rb_to_integer(val, :to_int);
    end

    def self.rb_to_integer(val, meth)
      return val if object_kind_of?(val, Integer)
      res = convert_type(val, Integer, meth, true)
      unless object_kind_of?(res, Integer)
        conversion_mismatch(val, Integer, meth, res)
      end
      res
    end

    def self.rb_to_float(val, meth)
      return val if object_kind_of?(val, Float)
      res = convert_type(val, Float, meth, true)
      unless object_kind_of?(res, Float)
        conversion_mismatch(val, Float, meth, res)
      end
      res
    end

    def self.conversion_mismatch(val, cls, meth, res)
      raise TypeError, "can't convert #{val.class} to #{cls} (#{val.class}##{meth} gives #{res.class})"
    end

    def self.check_int(val)
      unless Truffle.invoke_primitive(:fixnum_fits_into_int, val)
        raise RangeError, "integer #{val} too #{val < 0 ? 'small' : 'big'} to convert to `int"
      end
    end

    def self.check_uint(val)
      unless Truffle.invoke_primitive(:fixnum_fits_into_uint, val)
        raise RangeError, "integer #{val} too #{val < 0 ? 'small' : 'big'} to convert to `uint"
      end
    end

    def self.check_long(val)
      unless Truffle.invoke_primitive(:fixnum_fits_into_long, val)
        raise RangeError, "integer #{val} too #{val < 0 ? 'small' : 'big'} to convert to `long"
      end
    end

    def self.check_ulong(val)
      unless Truffle.invoke_primitive(:fixnum_fits_into_ulong, val)
        raise RangeError, "integer #{val} too #{val < 0 ? 'small' : 'big'} to convert to `ulong"
      end
    end

    def self.rb_check_to_integer(val, meth)
      return val if object_kind_of?(val, Fixnum)
      return val if object_kind_of?(val, Bignum)
      v = convert_type(val, Integer, meth, false)
      unless object_kind_of?(v, Integer)
        return nil
      end
      v
    end

    def self.rb_check_convert_type(obj, cls, meth)
      return obj if object_kind_of?(obj, cls)
      v = convert_type(obj, cls, meth, false)
      return nil if v.nil?
      unless object_kind_of?(v, cls)
        raise TypeError, "Coercion error: obj.#{meth} did NOT return a #{cls} (was #{object_class(v)})"
      end
      v
    end

    def self.rb_convert_type(obj, cls, meth)
      return obj if object_kind_of?(obj, cls)
      v = convert_type(obj, cls, meth, true)
      unless object_kind_of?(v, cls)
        raise TypeError, "Coercion error: obj.#{meth} did NOT return a #{cls} (was #{object_class(v)})"
      end
      v
    end

    def self.convert_type(obj, cls, meth, raise_on_error)
      r = check_funcall(obj, meth)
      if undefined.equal?(r)
        if raise_on_error
          raise TypeError, "can't convert #{obj.class} into #{cls} with #{meth}"
        end
        return nil
      end
      r
    end

    def self.check_funcall(recv, meth, args = [])
      check_funcall_default(recv, meth, args, undefined)
    end

    def self.check_funcall_default(recv, meth, args, default)
      if Truffle::Interop.foreign?(recv)
        if recv.respond_to?(meth)
          return recv.__send__(meth)
        else
          return default
        end
      end
      respond = check_funcall_respond_to(recv, meth, true)
      return default if respond == 0
      unless check_funcall_callable(recv, meth)
        return check_funcall_missing(recv, meth, args, respond, default);
      end
      recv.__send__(meth)
    end

    def self.check_funcall_respond_to(obj, meth, priv)
      # TODO Review BJF vm_respond_to
      return -1 unless object_respond_to_no_built_in?(obj, :respond_to?, true)
      if !!obj.__send__(:respond_to?, meth, true)
        1
      else
        0
      end
    end

    def self.check_funcall_missing(recv, meth, args, respond, default)
      ret = basic_obj_respond_to_missing(recv, meth, false) #PRIV false
      respond_to_missing = !undefined.equal?(ret)
      return default if respond_to_missing and !ret
      ret = default
      if object_respond_to_no_built_in?(recv, :method_missing, true)
        begin
          return recv.__send__(:method_missing, meth, *args)
        rescue NoMethodError
          # TODO BJF usually more is done here
          meth = Truffle.invoke_primitive :vm_method_lookup, recv, meth
          if meth
            ret = false
          else
            ret = respond_to_missing
          end
          if ret
            raise
          end
        end
      end
      undefined
    end

    def self.basic_obj_respond_to_missing(obj, mid, priv)
      return undefined unless object_respond_to_no_built_in?(obj, :respond_to_missing?, true)
      obj.__send__(:respond_to_missing?, mid, priv);
    end

    ##
    # Uses the logic of [Array, Hash, String].try_convert.
    #
    def self.try_convert(obj, cls, meth)
      return obj if object_kind_of?(obj, cls)
      return nil unless obj.respond_to?(meth)
      execute_try_convert(obj, cls, meth)
    end

    def self.execute_try_convert(obj, cls, meth)
      ret = obj.__send__(meth)

      return ret if ret.nil? || object_kind_of?(ret, cls)

      msg = "Coercion error: obj.#{meth} did NOT return a #{cls} (was #{object_class(ret)})"
      raise TypeError, msg
    end

    # Specific coercion methods

    def self.coerce_to_comparison(a, b)
      unless cmp = (a <=> b)
        raise ArgumentError, "comparison of #{a.inspect} with #{b.inspect} failed"
      end
      cmp
    end

    INT_MIN = -2147483648
    INT_MAX = 2147483647

    def self.clamp_to_int(n)
      if Truffle.invoke_primitive(:fixnum_fits_into_int, n)
        Truffle::Fixnum.lower(n)
      else
        n > 0 ? INT_MAX : INT_MIN
      end
    end

    def self.coerce_to_collection_index(index)
      return index if object_kind_of? index, Fixnum

      method = :to_int
      klass = Fixnum

      begin
        idx = index.__send__ method
      rescue => exc
        coerce_to_failed index, klass, method, exc
      end
      return idx if object_kind_of? idx, klass

      if object_kind_of? index, Bignum
        raise RangeError, 'Array index must be a Fixnum (passed Bignum)'
      else
        coerce_to_type_error index, idx, method, klass
      end
    end

    def self.coerce_to_collection_length(length)
      return length if object_kind_of? length, Fixnum

      method = :to_int
      klass = Fixnum

      begin
        size = length.__send__ method
      rescue => exc
        coerce_to_failed length, klass, method, exc
      end
      return size if object_kind_of? size, klass

      if object_kind_of? size, Bignum
        raise ArgumentError, 'Array size must be a Fixnum (passed Bignum)'
      else
        coerce_to_type_error length, size, :to_int, Fixnum
      end
    end

    def self.coerce_to_int(obj)
      if Integer === obj
        obj
      else
        coerce_to(obj, Integer, :to_int)
      end
    end

    def self.coerce_to_float(obj)
      case obj
      when Float
        obj
      when Numeric
        coerce_to obj, Float, :to_f
      when nil, true, false
        raise TypeError, "can't convert #{obj.inspect} into Float"
      else
        raise TypeError, "can't convert #{obj.class} into Float"
      end
    end

    def self.coerce_to_regexp(pattern, quote=false)
      case pattern
      when Regexp
        return pattern
      when String
        # nothing
      else
        pattern = StringValue(pattern)
      end

      pattern = Regexp.quote(pattern) if quote
      Regexp.new(pattern)
    end

    def self.coerce_to_encoding(obj)
      case obj
      when Encoding
        obj
      when String
        Encoding.find obj
      else
        Encoding.find StringValue(obj)
      end
    end

    def self.try_convert_to_encoding(obj)
      case obj
      when Encoding
        return obj
      when String
        str = obj
      else
        str = StringValue obj
      end

      key = str.upcase.to_sym

      pair = Encoding::EncodingMap[key]
      if pair
        index = pair.last
        return index && Truffle.invoke_primitive(:encoding_get_encoding_by_index, index)
      end

      false
    end

    def self.coerce_to_path(obj)
      if object_kind_of?(obj, String)
        obj
      else
        if object_respond_to? obj, :to_path
          obj = obj.to_path
        end

        StringValue(obj)
      end
    end

    def self.coerce_to_symbol(obj)
      return obj if object_kind_of? obj, Symbol

      obj = obj.to_str if obj.respond_to?(:to_str)
      coerce_to(obj, Symbol, :to_sym)
    end

    # Equivalent of num_exact in MRI's time.c; used by Time methods.
    def self.coerce_to_exact_num(obj)
      if obj.kind_of?(Integer)
        obj
      elsif obj.kind_of?(String)
        raise TypeError, "can't convert #{obj} into an exact number"
      elsif obj.nil?
        raise TypeError, "can't convert nil into an exact number"
      else
        check_convert_type(obj, Rational, :to_r) || coerce_to(obj, Integer, :to_int)
      end
    end

    def self.coerce_to_utc_offset(offset)
      offset = String.try_convert(offset) || offset

      if offset.kind_of?(String)
        unless offset.encoding.ascii_compatible? && offset.match(/\A(\+|-)(\d\d):(\d\d)\z/)
          raise ArgumentError, '"+HH:MM" or "-HH:MM" expected for utc_offset'
        end

        offset = $2.to_i*60*60 + $3.to_i*60
        offset = -offset if $1.ord == 45
      else
        offset = Rubinius::Type.coerce_to_exact_num(offset)
      end

      if Rational === offset
        offset = offset.round
      end

      if offset <= -86400 || offset >= 86400
        raise ArgumentError, 'utc_offset out of range'
      end

      offset
    end

    def self.coerce_to_bitwise_operand(obj)
      if object_kind_of? obj, Float
        raise TypeError, "can't convert Float into Integer for bitwise arithmetic"
      end
      coerce_to obj, Integer, :to_int
    end

    # String helpers

    def self.check_null_safe(string)
      raise ArgumentError, 'string contains NULL byte' if string.include? "\0"
      string
    end

    def self.binary_string(string)
      string.force_encoding Encoding::BINARY
    end

    def self.external_string(string)
      string.force_encoding Encoding.default_external
    end

    def self.encode_string(string, enc)
      string.force_encoding enc
    end

    def self.ascii_compatible_encoding(string)
      compatible_encoding string, Encoding::US_ASCII
    end

    def self.compatible_encoding(a, b)
      enc = Encoding.compatible? a, b

      unless enc
        enc_a = object_encoding a
        enc_b = object_encoding b
        message = 'undefined conversion '
        message << "for '#{a.inspect}' " if object_kind_of?(a, String)
        message << "from #{enc_a} to #{enc_b}"

        raise Encoding::CompatibilityError, message
      end

      enc
    end

    # Misc

    def self.rb_inspect(val)
      str = Rubinius::Type.coerce_to(val.inspect, String, :to_s)
      result_encoding = Encoding.default_internal || Encoding.default_external
      if str.ascii_only? || (result_encoding.ascii_compatible? && str.encoding == result_encoding)
        str
      else
        Truffle.invoke_primitive :string_escape, str
      end
    end

    def self.object_respond_to__dump?(obj)
      object_respond_to? obj, :_dump, true
    end

    def self.object_respond_to_marshal_dump?(obj)
      object_respond_to? obj, :marshal_dump, true
    end

    def self.object_respond_to_marshal_load?(obj)
      object_respond_to? obj, :marshal_load, true
    end

    def self.check_arity(arg_count, min, max)
      if arg_count < min || (max != -1 && arg_count > max)
        error_message = case
                        when min == max
                          'wrong number of arguments (given %d, expected %d)' % [arg_count, min]
                        when max == -1
                          'wrong number of arguments (given %d, expected %d+)' % [arg_count, min]
                        else
                          'wrong number of arguments (given %d, expected %d..%d)' % [arg_count, min, max]
                        end
        raise ArgumentError, error_message
      end
    end

    def self.check_safe_level(level)
      level = rb_num2int(level)
      current_level = Thread.current.safe_level
      if level < current_level
        raise SecurityError, "tried to downgrade safe level from #{current_level} to #{level}"
      end
      if level > 1 # SAFE_LEVEL_MAX
        raise ArgumentError, '$SAFE=2 to 4 are obsolete'
      end
      level
    end

  end
end
