# frozen_string_literal: true

# Copyright (c) 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module FiberOperations
    def self.validate_storage(storage)
      return if Primitive.nil?(storage)

      unless Primitive.is_a?(storage, Hash)
        Kernel.raise TypeError, 'storage must be a hash'
      end

      if storage.frozen?
        Kernel.raise FrozenError, 'storage must not be frozen'
      end

      storage.each_key do |key|
        unless Primitive.is_a?(key, Symbol)
          Kernel.raise TypeError, "#{key.inspect} is not a symbol"
        end
      end
    end

    def self.get_storage_for_access(allocate)
      fiber = Fiber.current
      storage = Primitive.fiber_get_storage(fiber)

      if Primitive.nil?(storage) && allocate
        storage = {}
        Primitive.fiber_set_storage(fiber, storage)
      end

      storage
    end
  end
end
