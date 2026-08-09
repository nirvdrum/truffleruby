# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

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

ENV = Object.new

class << ENV
  include Enumerable

  # The process environment is the single source of truth, rather than a copy
  # held on the Ruby side, so that variables set by C code after boot, such as
  # a native extension calling setenv() or putenv(), are visible here, exactly
  # as in CRuby.
  #
  # That means every access goes through getenv()/environ, which POSIX leaves
  # undefined when it runs concurrently with setenv()/unsetenv(). CRuby is
  # shielded from this by the GVL; we have no GVL, so ENV serializes its own
  # accesses with TruffleRuby.synchronized(self). Reading the environment is
  # slow enough in any Ruby implementation that it is not expected on a hot
  # path, so a plain exclusive lock is preferred to anything more elaborate.
  # The lock is reentrant, so code running under it may call back into ENV;
  # even so, blocks supplied by the caller are run with no lock held, to keep
  # arbitrary user code from ordering itself against other threads' ENV access.
  #
  # This orders accesses made through ENV and nothing else. Native code calling
  # setenv() on its own thread does not take this lock and cannot be protected
  # against, exactly as in CRuby.

  def size
    environ_entries.size
  end
  alias_method :length, :size

  # A single variable is read with getenv() rather than by searching a snapshot
  # of environ, since that is what the C library is optimized for.
  private def lookup(key)
    key = Primitive.convert_with_to_str(key)
    value = TruffleRuby.synchronized(self) { Truffle::POSIX.getenv(key) }
    value && set_encoding(value)
  end

  # A pointer to the `environ` symbol itself. Its address never changes, so it
  # is worth caching, unlike the array it points at, which setenv() is free to
  # reallocate and which must therefore be re-read on every use. Two threads
  # racing here both compute the same pointer, so no synchronization is needed.
  private def environ_pointer
    @environ_pointer ||= Truffle::POSIX.truffleposix_environ_address
  end

  # Returns the whole environment as an Array of [key, value] pairs holding the
  # bytes from environ, which is walked in place like MRI's env_keys and
  # env_each_pair do, tagged with the locale encoding by #environ_string.
  # Callers apply #set_encoding to whichever parts they hand back to the caller.
  private def environ_entries
    TruffleRuby.synchronized(self) { environ_entries_unlocked }
  end

  # Must be called with the ENV lock held, since the entries are pointers into
  # environ, which setenv() may free.
  private def environ_entries_unlocked
    array = environ_pointer.read_pointer

    entries = []
    index = 0
    until (entry = array.get_pointer(index * Truffle::FFI::Pointer::SIZE)).null?
      string = entry.read_string_to_null
      separator = string.index('=')
      # An environ entry without a '=' is not a variable, and MRI's env_each
      # skips it as well.
      entries << [environ_string(string[0...separator]), environ_string(string[(separator + 1)..-1])] if separator
      index += 1
    end

    entries
  end

  def [](key)
    lookup(key)
  end

  def []=(key, value)
    key = Primitive.convert_with_to_str(key)
    TruffleRuby.synchronized(self) { env_set(key, value) }
    value
  end
  alias_method :store, :[]=

  # Must be called with the ENV lock held, since setenv() and unsetenv() may
  # not run concurrently with any other access to environ.
  private def env_set(key, value)
    if Primitive.nil? value
      Truffle::POSIX.unsetenv(key)
    else
      if Truffle::POSIX.setenv(key, Primitive.convert_with_to_str(value), 1) != 0
        Errno.handle('setenv')
      end
    end
  end

  def clone
    raise TypeError, 'Cannot clone ENV, use ENV.to_h to get a copy of ENV as a hash'
  end

  def delete(key)
    key = Primitive.convert_with_to_str(key)
    # The read and the unsetenv() must not be separated by another thread's
    # write, so both are done in a single critical section.
    existing_value = TruffleRuby.synchronized(self) do
      value = Truffle::POSIX.getenv(key)
      Truffle::POSIX.unsetenv(key) if value
      value
    end

    if existing_value
      set_encoding(existing_value)
    elsif block_given?
      yield key
    end
  end

  def dup
    raise TypeError, 'Cannot dup ENV, use ENV.to_h to get a copy of ENV as a hash'
  end

  def shift
    TruffleRuby.synchronized(self) do
      entry = environ_entries_unlocked.first
      next nil unless entry

      key, value = entry
      Truffle::POSIX.unsetenv(key)

      [set_encoding(key), set_encoding(value)]
    end
  end

  def each
    return to_enum(:each) { size } unless block_given?

    # Snapshot the entries, like MRI's ENV#each does, so that iteration is
    # consistent even if ENV is mutated, and the block runs without any lock
    # and may itself call ENV methods.  Keys and values are transcoded to
    # Encoding.default_internal, like MRI's env_each_pair does.
    environ_entries.each do |key, value|
      yield set_encoding(key), set_encoding(value)
    end

    self
  end
  alias_method :each_pair, :each

  def each_key
    return to_enum(:each_key) { size } unless block_given?
    environ_entries.each do |key, _value|
      yield set_encoding(key)
    end
    self
  end

  def each_value
    return to_enum(:each_value) { size } unless block_given?

    each { |_k, v| yield v }
  end

  def delete_if(&block)
    return to_enum(:delete_if) { size } unless block_given?
    reject!(&block)
    self
  end

  # More efficient than using the one from Enumerable
  def include?(key)
    !Primitive.nil?(lookup(key))
  end
  alias_method :has_key?, :include?
  alias_method :key?, :include?
  alias_method :member?, :include?

  def fetch(key, absent = undefined)
    if block_given? and !Primitive.undefined?(absent)
      Primitive.warn_block_supersedes_default_value_argument
    end

    if value = lookup(key)
      return value
    end

    if block_given?
      return yield(key)
    elsif Primitive.undefined?(absent)
      raise KeyError.new("key not found: #{key.inspect}", receiver: self, key: key)
    end

    absent
  end

  def to_s
    'ENV'
  end

  def inspect
    to_hash.inspect
  end

  def reject(&block)
    to_hash.reject(&block)
  end

  def reject!
    return to_enum(:reject!) { size } unless block_given?

    # Run the block outside the lock, as it may itself call ENV methods, then
    # delete the selected keys in a single critical section.
    keys = []
    each { |k, v| keys << k if yield(k, v) }
    unless keys.empty?
      TruffleRuby.synchronized(self) do
        keys.each do |key|
          Truffle::POSIX.unsetenv(key)
        end
      end
    end

    keys.empty? ? nil : self
  end

  def clear
    # Hold the lock for the whole operation rather than acquiring it once per key.
    TruffleRuby.synchronized(self) do
      environ_entries_unlocked.each do |key, _value|
        Truffle::POSIX.unsetenv(key)
      end
    end

    self
  end

  def has_value?(value)
    value = Truffle::Type.rb_check_convert_type(value, String, :to_str)
    return nil if Primitive.nil? value
    each { |_k, v| return true if v == value }
    false
  end
  alias_method :value?, :has_value?

  def values_at(*params)
    params.map { |k| lookup(k) }
  end

  def invert
    to_hash.invert
  end

  def key(value)
    value = Primitive.convert_with_to_str(value);
    each do |k, v|
      return k if v == value
    end
    nil
  end

  def keys
    keys = []
    each { |k, _v| keys << k }
    keys
  end

  def values
    vals = []
    each { |_k, v| vals << v }
    vals
  end

  def empty?
    each { return false }
    true
  end

  def rehash
    # No need to do anything, our keys are always strings
  end

  def replace(other)
    return self if Primitive.equal?(self, other)
    other = Primitive.convert_with_to_hash(other)

    # Hold the lock for the whole operation rather than acquiring it once per
    # key, as ENV#replace is typically called with a large hash. Each key is
    # converted as it is applied, not up front, so that a conversion error
    # leaves the preceding writes in place like MRI's env_replace does. The
    # conversions may run arbitrary Ruby code, but the lock is reentrant, so
    # code that itself reads or writes ENV will not deadlock. The keys are
    # matched by their bytes, as MRI's keylist_delete does, since a key
    # supplied by the caller need not be in the locale encoding of the keys
    # read from environ, and String#== treats non-ASCII strings in different
    # encodings as unequal.
    TruffleRuby.synchronized(self) do
      keys_to_delete = environ_entries_unlocked.to_h { |key, _value| [key.b, key] }

      other.each do |k, v|
        key = Primitive.convert_with_to_str(k)
        env_set(key, v)
        keys_to_delete.delete(key.b)
      end

      keys_to_delete.each_value do |key|
        Truffle::POSIX.unsetenv(key)
      end
    end

    self
  end

  def select(&blk)
    return to_enum(:select) { size } unless block_given?
    to_hash.select(&blk)
  end
  alias_method :filter, :select

  def to_a
    ary = []
    each { |k, v| ary << [k, v] }
    ary
  end

  def to_hash
    h = {}
    each_pair do |key, value|
      h[key] = value
    end
    h
  end

  def to_h
    return to_hash unless block_given?

    h = {}
    each_pair do |k, v|
      pair = yield(k, v)
      Truffle::HashOperations.assoc_key_value_pair(h, pair)
    end
    h
  end

  def update(*others)
    others.each do |other|
      next if Primitive.equal?(self, other)

      other = Primitive.convert_with_to_hash(other)

      if block_given?
        other.each do |k, v|
          if include?(k)
            self[k] = yield(k, lookup(k), v)
          else
            self[k] = v
          end
        end
      else
        # Hold the lock for the whole operation rather than acquiring it once
        # per key. Each key is converted as it is applied, not up front, so
        # that a conversion error leaves the preceding writes in place like
        # MRI's env_update does.
        TruffleRuby.synchronized(self) do
          other.each do |k, v|
            env_set(Primitive.convert_with_to_str(k), v)
          end
        end
      end
    end

    self
  end
  alias_method :merge!, :update

  def keep_if(&block)
    return to_enum(:keep_if) { size } unless block_given?
    select!(&block)
    self
  end

  def select!
    return to_enum(:select!) { size } unless block_given?
    reject! { |k, v| !yield(k, v) }
  end
  alias_method :filter!, :select!

  def assoc(key)
    key = Primitive.convert_with_to_str(key)
    value = lookup(key)
    value ? [key, value] : nil
  end

  def rassoc(value)
    value = Truffle::Type.rb_check_convert_type(value, String, :to_str)
    return nil if Primitive.nil? value
    key = key(value)
    key ? [key, value] : nil
  end

  def slice(*keys)
    result = {}
    keys.each do |k|
      value = lookup(k)
      unless Primitive.nil? value
        result[k] = value
      end
    end
    result
  end

  def except(*keys)
    # More memory-efficient than delegating to Hash.except
    result = to_hash
    keys.each { |k| result.delete(k) }

    result
  end

  # Strings read from environ arrive as BINARY. They are tagged with the locale
  # encoding, like MRI's env_str_new does, without copying or transcoding the
  # bytes, so that a key found in environ can be passed straight back to
  # unsetenv() and compared against keys supplied by the caller: a BINARY key
  # holding non-ASCII bytes would compare unequal to the caller's LOCALE key
  # for the same variable. The US-ASCII locale case matches #set_encoding.
  private def environ_string(string)
    if Encoding::LOCALE == Encoding::US_ASCII && !string.ascii_only?
      string.force_encoding(Encoding::BINARY)
    else
      string.force_encoding(Encoding::LOCALE)
    end
  end

  def set_encoding(value)
    return unless Primitive.is_a?(value, String)
    if Encoding.default_internal && value.ascii_only?
      value = value.encode Encoding.default_internal, Encoding::LOCALE
    elsif value.encoding != Encoding::LOCALE
      if Encoding::LOCALE == Encoding::US_ASCII && !value.ascii_only?
        value = value.b
      else
        value = value.dup.force_encoding(Encoding::LOCALE)
      end
    end
    value.freeze
  end
  private :set_encoding
end

# JRuby uses this for example to make proxy settings visible to stdlib/uri/common.rb

ENV_JAVA = {}
