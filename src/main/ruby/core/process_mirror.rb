# Copyright (c) 2015, 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

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

# Copyright (C) 1993-2017 Yukihiro Matsumoto. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
#
# 1. Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright
# notice, this list of conditions and the following disclaimer in the
# documentation and/or other materials provided with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
# ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
# ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
# OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
# HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
# LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
# OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
# SUCH DAMAGE.

module Rubinius
  class Mirror
    module Process
      SHELL_META_CHARS = [
          '*',  # Pathname Expansion
          '?',  # Pathname Expansion
          '{',  # Grouping Commands
          '}',  # Grouping Commands
          '[',  # Pathname Expansion
          ']',  # Pathname Expansion
          '<',  # Redirection
          '>',  # Redirection
          '(',  # Grouping Commands
          ')',  # Grouping Commands
          '~',  # Tilde Expansion
          '&',  # AND Lists, Asynchronous Lists
          '|',  # OR Lists, Pipelines
          '\\', # Escape Character
          '$',  # Parameter Expansion
          ';',  # Sequential Lists
          '\'', # Single-Quotes
          '`',  # Command Substitution
          '"',  # Double-Quotes
          "\n", # Lists
          '#',  # Comment
          '=',  # Assignment preceding command name
          '%'   # (used in Parameter Expansion)
      ]
      SHELL_META_CHAR_PATTERN = Regexp.new("[#{SHELL_META_CHARS.map(&Regexp.method(:escape)).join}]")

      def self.exec(*args)
        exe = Execute.new(*args)
        exe.spawn_setup(true)
        exe.exec(exe.command, exe.argv, exe.env_array)
      end

      def self.spawn(*args)
        exe = Execute.new(*args)
        exe.spawn_setup(false)
        exe.spawn(exe.options, exe.command, exe.argv)
      end

      class Execute
        attr_reader :command
        attr_reader :argv
        attr_reader :options
        attr_reader :env_array

        # Turns the various varargs incantations supported by Process.spawn into a
        # [env, prog, argv, redirects, options] tuple.
        #
        # The following method signature is supported:
        #   Process.spawn([env], command, ..., [options])
        #
        # The env and options hashes are optional. The command may be a variable
        # number of strings or an Array full of strings that make up the new process's
        # argv.
        #
        # Assigns @environment, @command, @argv, @redirects, @options. All
        # elements are guaranteed to be non-nil. When no env or options are
        # given, empty hashes are returned.
        def initialize(env_or_cmd, *args)
          if options = Rubinius::Type.try_convert(args.last, ::Hash, :to_hash)
            args.pop
          end

          if env = Rubinius::Type.try_convert(env_or_cmd, ::Hash, :to_hash)
            unless command = args.shift
              raise ArgumentError, 'command argument expected'
            end
          else
            command = env_or_cmd
          end

          if args.empty? and cmd = Rubinius::Type.try_convert(command, ::String, :to_str)
            @command = cmd
            @argv = []
          else
            if cmd = Rubinius::Type.try_convert(command, ::Array, :to_ary)
              raise ArgumentError, 'wrong first argument' unless cmd.size == 2
              command = StringValue(cmd[0])
              name = StringValue(cmd[1])
            else
              name = command = StringValue(command)
            end

            argv = [name]
            args.each { |arg| argv << StringValue(arg) }

            @command = command
            @argv = argv
          end

          @command = Rubinius::Type.check_null_safe(StringValue(@command))

          if @argv.empty? # A single String for both command and arguments
            if should_use_shell?(@command)
              @command, @argv = '/bin/sh', ['sh', '-c', @command]
            else
              # No shell processing, and posix_spawnp will do the lookup of the command if needed
              @command = @command.strip
              if @command.empty?
                # Special case for command == "" as split does not work
                @argv = [@command]
              else
                split_command, *split_args = @command.split(' ')
                @command = split_command
                @argv = [split_command] + split_args
              end
            end
          else
            # If arguments are explicitly passed, the semantics of this method (defined in Ruby) are to run the
            # command directly. posix_spawnp(3) will lookup the full path to the command if needed.
          end

          @options = {}
          @files_for_child = []

          if options
            options.each do |key, value|
              case key
              when ::IO, ::Fixnum, :in, :out, :err
                from = convert_io_fd key
                to = convert_to_fd value, from, @files_for_child
                redirect @options, from, to
              when ::Array

                # We must take care here that we do the right things
                # when redirecting multiple fds to the same target. We
                # will do this by handling the first fd normally, and
                # redirecting the other fds to it. We also require
                # that the underlying spawn implementation will handle
                # the initial fd assignment before doing the other fd
                # redirection.

                from = convert_io_fd key.first
                to = convert_to_fd value, from, @files_for_child
                redirect @options, from, to
                key[1..-1].each { |k| redirect @options, convert_io_fd(k), from }
              when :unsetenv_others
                if value
                  @options[:unsetenv_others] = true
                end
              when :pgroup
                if value == true
                  value = 0
                elsif value
                  value = Rubinius::Type.coerce_to value, ::Integer, :to_int
                  raise ArgumentError, "negative process group ID : #{value}" if value < 0
                else
                  value = -1
                end
                @options[key] = value
              when :chdir
                @options[key] = Rubinius::Type.coerce_to_path(value)
              when :umask
                @options[key] = value
              when :close_others
                @options[key] = value if value
              else
                raise ArgumentError, "unknown exec option: #{key.inspect}"
              end
            end
          end

          if env
            array = (@options[:env] ||= [])

            env.each do |key, value|
              array << [convert_env_key(key), convert_env_value(value)]
            end
          end
        end

        def redirect(options, from, to)
          case to
          when ::Fixnum
            map = (options[:redirect_fd] ||= [])
            map << from << to
          when ::Array
            map = (options[:assign_fd] ||= [])
            map << from
            map.concat to
          end
        end

        def convert_io_fd(obj)
          case obj
          when ::Fixnum
            obj
          when :in
            0
          when :out
            1
          when :err
            2
          when ::IO
            obj.fileno
          else
            raise ArgumentError, "wrong exec option: #{obj.inspect}"
          end
        end

        def convert_to_fd(obj, target, file_list)
          case obj
          when ::Fixnum
            obj
          when :in
            0
          when :out
            1
          when :err
            2
          when :close
            nil
          when ::IO
            obj.fileno
          when ::String
            open_file_for_child(file_list, obj, default_mode(target), 0644)
          when ::Array
            case obj.size
            when 1
              open_file_for_child(file_list, obj[0], File::RDONLY, 0644)
            when 2
              if obj[0] == :child
                fd = convert_to_fd obj[1], target, file_list
                fd.kind_of?(::Fixnum) ?  -(fd + 1) : fd
              else
                open_file_for_child(file_list, obj[0], convert_file_mode(obj[1]), 0644)
              end
            when 3
              open_file_for_child(file_list, obj[0], convert_file_mode(obj[1]), obj[2])
            end
          else
            raise ArgumentError, "wrong exec redirect: #{obj.inspect}"
          end
        end

        def open_file_for_child(file_list, name, mode, perms)
          f = File.new(name, mode, perms)
          file_list << f
          f.fileno
        end

        def default_mode(target)
          if target == 1 or target == 2
            OFLAGS['w']
          else
            OFLAGS['r']
          end
        end

        def convert_file_mode(obj)
          case obj
          when ::Fixnum
            obj
          when ::String
            OFLAGS[obj]
          when nil
            OFLAGS['r']
          else
            Rubinius::Type.coerce_to obj, Integer, :to_int
          end
        end

        def convert_env_key(key)
          key = Rubinius::Type.check_null_safe(StringValue(key))

          if key.include?('=')
            raise ArgumentError, "environment name contains a equal : #{key}"
          end

          key
        end

        def convert_env_value(value)
          return if value.nil?
          Rubinius::Type.check_null_safe(StringValue(value))
        end

        # Mapping of string open modes to integer oflag versions.
        OFLAGS = {
          'r'  => ::File::RDONLY,
          'r+' => ::File::RDWR   | ::File::CREAT,
          'w'  => ::File::WRONLY | ::File::CREAT  | ::File::TRUNC,
          'w+' => ::File::RDWR   | ::File::CREAT  | ::File::TRUNC,
          'a'  => ::File::WRONLY | ::File::APPEND | ::File::CREAT,
          'a+' => ::File::RDWR   | ::File::APPEND | ::File::CREAT
        }

        def spawn_setup(alter_process)
          env = options.delete(:unsetenv_others) ? {} : ENV.to_hash
          if add_to_env = options.delete(:env)
            env.merge! Hash[add_to_env]
          end

          @env_array = env.map { |k, v| "#{k}=#{v}" }

          if alter_process
            require 'fcntl'

            if pgroup = options[:pgroup]
              Truffle::POSIX.setpgid(0, pgroup)
            end

            if mask = options[:mask]
              Truffle::POSIX.umask(mask)
            end

            if chdir = options[:chdir]
              Truffle::POSIX.chdir(chdir)
            end

            if _close_others = options[:close_others]
              warn 'spawn_setup: close_others not yet implemented'
            end

            if assign_fd = options[:assign_fd]
              assign_fd.each_slice(4) do |from, name, mode, perm|
                to = IO.sysopen(name, mode | Fcntl::FD_CLOEXEC, perm)
                redirect_file_descriptor(from, to)
              end
            end

            if redirect_fd = options[:redirect_fd]
              redirect_fd.each_slice(2) do |from, to|
                redirect_file_descriptor(from, to)
              end
            end
          end

          nil
        end

        def redirect_file_descriptor(from, to)
          to = (-to + 1) if to < 0

          result = Truffle::POSIX.dup2(to, from)
          Errno.handle if result < 0

          flags = Truffle::POSIX.fcntl(from, Fcntl::F_GETFD, nil)
          Errno.handle if flags < 0

          Truffle::POSIX.fcntl(from, Fcntl::F_SETFD, flags & ~Fcntl::FD_CLOEXEC)
        end

        def spawn(options, command, arguments)
          pid = Truffle::Process.spawn command, arguments, env_array, options
          @files_for_child.each { |f| f.close }
          # Check if the command exists *after* invoking posix_spawn so we have a pid
          unless resolve_in_path(command)
            if pid < 0
              # macOS posix_spawnp(3) returns -1 and no pid when the command is not found,
              # Linux returns 0, sets the pid and let the child do the PATH lookup.
              $? = ::Process::Status.new(pid, 127, nil, nil)
            else
              # the subprocess will fail, just wait for it
              ::Process.wait(pid) # Sets $? and avoids a zombie process
              unless $?.exitstatus == 127
                raise "command #{command} does not exist in PATH but posix_spawnp found it!"
              end
            end
            raise Errno::ENOENT, "No such file or directory - #{command}"
          end
          pid
        end

        def exec(command, args, env_array)
          # exec validates the command only if it searches in $PATH
          if should_search_path?(command)
            if resolved = resolve_in_path(command)
              command = resolved
            else
              raise Errno::ENOENT, "No such file or directory - #{command}"
            end
          end
          Truffle.invoke_primitive :vm_exec, command, args, env_array
          raise PrimitiveFailure, 'Rubinius::Mirror::Process::Execute#exec primitive failed'
        end

        def should_use_shell?(command)
          command.match(SHELL_META_CHAR_PATTERN)
        end

        def should_search_path?(command)
          !command.include?(File::SEPARATOR)
        end

        def resolve_in_path(command)
          unless should_search_path?(command)
            if File.file?(command) && File.executable?(command)
              return command
            else
              return nil
            end
          end

          ENV['PATH'].split(File::PATH_SEPARATOR).each do |dir|
            file = File.join(dir, command)
            if File.file?(file) && File.executable?(file)
              return file
            end
          end

          nil
        end
      end
    end
  end
end
