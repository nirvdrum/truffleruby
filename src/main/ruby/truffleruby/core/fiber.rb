# frozen_string_literal: true

# Copyright (c) 2021, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class Fiber
  def initialize(blocking: false, storage: true, &block)
    # Handle storage parameter per CRuby semantics
    if Primitive.undefined?(storage) || storage == true
      # Inherit from parent (shallow copy)
      parent_storage = Primitive.fiber_get_storage(Fiber.current)
      storage = Primitive.nil?(parent_storage) ? nil : parent_storage.dup
    elsif !Primitive.nil?(storage)
      # Validate and dup provided hash
      Truffle::FiberOperations.validate_storage(storage)
      storage = storage.dup
    # else: nil means lazy initialization (keep as nil)
    end

    Primitive.fiber_initialize(self, Primitive.as_boolean(blocking), storage, block)
  end

  def raise(*args)
    exc = Truffle::ExceptionOperations.make_exception(args)
    exc = RuntimeError.new('') unless exc
    Primitive.fiber_raise(self, exc)
  end

  def inspect
    loc = Primitive.fiber_source_location(self)
    status = Primitive.fiber_status(self)
    "#{super.delete_suffix('>')} #{loc} (#{status})>"
  end
  alias_method :to_s, :inspect

  def self.[](key)
    key = Truffle::Type.coerce_to_symbol(key)
    storage = Truffle::FiberOperations.get_storage_for_access(false)
    Primitive.nil?(storage) ? nil : storage[key]
  end

  def self.[]=(key, value)
    key = Truffle::Type.coerce_to_symbol(key)
    storage = Truffle::FiberOperations.get_storage_for_access(!Primitive.nil?(value))

    if Primitive.nil?(value)
      # CRuby semantics: setting nil deletes the key
      storage.delete(key) unless Primitive.nil?(storage)
    else
      storage[key] = value
    end

    value
  end

  def storage
    unless Primitive.equal?(self, Fiber.current)
      Kernel.raise ArgumentError, 'Fiber storage can only be accessed from the Fiber it belongs to'
    end

    storage = Primitive.fiber_get_storage(self)
    Primitive.nil?(storage) ? nil : storage.dup
  end

  def storage=(value)
    # Experimental warning
    if Warning[:experimental]
      Warning.warn('Fiber#storage= is experimental and may be removed in the future!',
                   category: :experimental)
    end

    unless Primitive.equal?(self, Fiber.current)
      Kernel.raise ArgumentError, 'Fiber storage can only be accessed from the Fiber it belongs to'
    end

    Truffle::FiberOperations.validate_storage(value)
    storage = Primitive.nil?(value) ? nil : value.dup
    Primitive.fiber_set_storage(self, storage)

    value
  end
end
