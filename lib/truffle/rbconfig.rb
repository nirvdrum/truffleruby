# frozen-string-literal: false
#
# Copyright (c) 2014, 2019 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
#
# Copyright (C) 1993-2013 Yukihiro Matsumoto. All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions
# are met:
# 1. Redistributions of source code must retain the above copyright
# notice, this list of conditions and the following disclaimer.
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
#
#  RbConfig.expand method imported from MRI sources
#

module RbConfig

  ruby_home = Truffle::Boot.ruby_home
  raise 'The TruffleRuby home needs to be set to require RbConfig' unless ruby_home
  TOPDIR = ruby_home

  host_os = Truffle::System.host_os
  host_cpu = Truffle::System.host_cpu
  host_vendor = 'unknown'
  # Some config entries report linux-gnu rather than simply linux.
  host_os_full = host_os == 'linux' ? 'linux-gnu' : host_os
  # Host should match the target triplet for clang or GCC, otherwise some C extension builds will fail.
  host = "#{host_cpu}-#{host_vendor}-#{host_os_full}"

  ruby_install_name = 'truffleruby'
  ruby_base_name = 'ruby'

  # The full TruffleRuby version, so C extensions from one TruffleRuby version
  # are not reused with another TruffleRuby version.
  ruby_abi_version = RUBY_ENGINE_VERSION

  arch = "#{host_cpu}-#{host_os}"
  libs = ''

  prefix = ruby_home
  graalvm_home = Truffle::System.get_java_property 'org.graalvm.home'
  extra_bindirs = if graalvm_home
                    jre_bin = "#{graalvm_home}/jre/bin"
                    ["#{graalvm_home}/bin", *(jre_bin if File.directory?(jre_bin))]
                  else
                    []
                  end

  cc = Truffle::Boot.tool_path(:CC)
  cxx = Truffle::Boot.tool_path(:CXX)
  ar = Truffle::Boot.tool_path(:AR)
  strip = Truffle::Boot.tool_path(:STRIP)

  # Determine the various flags for native compilation
  optflags = ''
  debugflags = ''
  warnflags = [
    '-Wimplicit-function-declaration', # To make missing C ext functions clear
    '-Wno-int-conversion',             # MRI has VALUE defined as long while we have it as void*
    '-Wno-int-to-pointer-cast',        # Same as above
    '-Wno-incompatible-pointer-types', # Fix byebug 8.2.1 compile (st_data_t error)
    '-Wno-format-invalid-specifier',   # Our PRIsVALUE generates this because compilers ignore printf extensions
    '-Wno-format-extra-args',          # Our PRIsVALUE generates this because compilers ignore printf extensions
    '-ferror-limit=500'
  ]

  defs = ''
  cppflags = ''
  ldflags = ''
  dldflags = Truffle::Platform.darwin? ? '-Wl,-undefined,dynamic_lookup -Wl,-multiply_defined,suppress' : ''

  cext_dir = "#{prefix}/lib/cext"
  dlext = Truffle::Platform::DLEXT

  # Make C extensions use the same libssl as the one used for the openssl C extension
  if Truffle::Platform.darwin?
    require 'truffle/openssl-prefix'
    openssl_prefix = ENV['OPENSSL_PREFIX']
    if openssl_prefix
      # Change the same variables as MRI's --with-opt-dir configure option would
      cppflags << " -I#{openssl_prefix}/include"
      ldflags << " -L#{openssl_prefix}/lib"
      dldflags << " -L#{openssl_prefix}/lib"
    end
  end

  # Set extra flags needed for --building-core-cexts
  if Truffle::Boot.get_option 'building-core-cexts'
    libtruffleruby = "#{ruby_home}/src/main/c/cext/libtruffleruby.#{dlext}"

    relative_debug_paths = "-fdebug-prefix-map=#{ruby_home}=."
    cppflags << relative_debug_paths

    warnflags << '-Wundef' # Warn for undefined preprocessor macros for core C extensions
    warnflags << '-Werror' # Make sure there are no warnings in core C extensions
  else
    libtruffleruby = "#{cext_dir}/libtruffleruby.#{dlext}"

    # GR-19453: workaround for finding libc++.so when using NFI on the library since the toolchain does not pass -rpath automatically
    rpath_libcxx = " -rpath #{File.expand_path("../../lib", cc)}"
    ldflags << rpath_libcxx
    dldflags << rpath_libcxx
  end

  # Link to libtruffleruby by absolute path
  libtruffleruby_dir = File.dirname(libtruffleruby)
  librubyarg = "-L#{libtruffleruby_dir} -rpath #{libtruffleruby_dir} -ltruffleruby -lpolyglot-mock"

  warnflags = warnflags.join(' ')

  # Sorted alphabetically using sort(1)
  CONFIG = {
    'AR'                => ar,
    'arch'              => arch,
    'ARCH_FLAG'         => '',
    'build'             => host,
    'build_os'          => host_os_full,
    'configure_args'    => ' ',
    'CC'                => cc,
    'CCDLFLAGS'         => '-fPIC',
    'CXX'               => cxx,
    'COUTFLAG'          => '-o ',
    'cppflags'          => cppflags,
    'CPPOUTFILE'        => '-o conftest.i',
    'debugflags'        => debugflags,
    'DEFS'              => defs,
    'DLDFLAGS'          => dldflags,
    'DLDLIBS'           => '',
    'DLEXT'             => dlext.dup,
    'ENABLE_SHARED'     => 'yes', # We use a dynamic library for libruby
    'EXECUTABLE_EXTS'   => '',
    'exeext'            => '',
    'EXEEXT'            => '',
    'extra_bindirs'     => extra_bindirs.join(File::PATH_SEPARATOR),
    'host_alias'        => '',
    'host_cpu'          => host_cpu,
    'host'              => host,
    'host_os'           => host_os_full,
    'includedir'        => "#{prefix}/lib/cext", # the parent dir of rubyhdrdir
    'LDFLAGS'           => ldflags,
    'libdirname'        => 'libdir',
    'LIBEXT'            => 'a',
    'LIBPATHENV'        => 'LD_LIBRARY_PATH',
    'LIBRUBY'           => '',
    'LIBRUBY_A'         => '',
    'LIBRUBYARG'        => librubyarg,
    'LIBRUBYARG_SHARED' => librubyarg,
    'LIBRUBYARG_STATIC' => '',
    'LIBRUBY_SO'        => "cext/libtruffleruby.#{Truffle::Platform::SOEXT}",
    'LIBS'              => libs,
    'NULLCMD'           => ':',
    'OBJEXT'            => 'o',
    'optflags'          => optflags,
    'OUTFLAG'           => '-o ',
    'PATH_SEPARATOR'    => File::PATH_SEPARATOR.dup,
    'prefix'            => prefix,
    'RM'                => 'rm -f',
    'RUBY_BASE_NAME'    => ruby_base_name,
    'ruby_install_name' => ruby_install_name,
    'RUBY_INSTALL_NAME' => ruby_install_name,
    'ruby_version'      => ruby_abi_version.dup,
    'rubyarchhdrdir'    => "#{prefix}/lib/cext/include",
    'rubyhdrdir'        => "#{prefix}/lib/cext/include",
    'SOEXT'             => Truffle::Platform::SOEXT.dup,
    'STRIP'             => strip,
    'sysconfdir'        => "#{prefix}/etc", # doesn't exist, as in MRI
    'target_cpu'        => host_cpu,
    'target_os'         => host_os,
    'UNICODE_VERSION'   => '12.0.0',
    'UNICODE_EMOJI_VERSION' => '12.0',
    'warnflags'         => warnflags,
  }

  MAKEFILE_CONFIG = CONFIG.dup

  expanded = CONFIG
  mkconfig = MAKEFILE_CONFIG

  expanded['RUBY_SO_NAME'] = ruby_base_name
  mkconfig['RUBY_SO_NAME'] = '$(RUBY_BASE_NAME)'

  exec_prefix = \
  expanded['exec_prefix'] = prefix
  mkconfig['exec_prefix'] = '$(prefix)'
  bindir = \
  expanded['bindir'] = "#{exec_prefix}/bin"
  mkconfig['bindir'] = '$(exec_prefix)/bin'
  libdir = \
  expanded['libdir'] = "#{exec_prefix}/lib"
  mkconfig['libdir'] = '$(exec_prefix)/lib'
  rubylibprefix = \
  expanded['rubylibprefix'] = "#{libdir}/#{ruby_base_name}"
  mkconfig['rubylibprefix'] = '$(libdir)/$(RUBY_BASE_NAME)'
  rubylibdir = \
  expanded['rubylibdir'] = "#{libdir}/mri"
  mkconfig['rubylibdir'] = '$(libdir)/mri'
  rubyarchdir = \
  expanded['rubyarchdir'] = rubylibdir
  mkconfig['rubyarchdir'] = '$(rubylibdir)'
  archdir = \
  expanded['archdir'] = rubyarchdir
  mkconfig['archdir'] = '$(rubyarchdir)'
  sitearch = \
  expanded['sitearch'] = arch
  mkconfig['sitearch'] = '$(arch)'
  sitedir = \
  expanded['sitedir'] = "#{rubylibprefix}/site_ruby"
  mkconfig['sitedir'] = '$(rubylibprefix)/site_ruby'
  # Must be kept in sync with post.rb
  sitelibdir = \
  expanded['sitelibdir'] = "#{sitedir}/#{ruby_abi_version}"
  mkconfig['sitelibdir'] = '$(sitedir)/$(ruby_version)'
  expanded['sitearchdir'] = "#{sitelibdir}/#{sitearch}"
  mkconfig['sitearchdir'] = '$(sitelibdir)/$(sitearch)'
  expanded['topdir'] = archdir
  mkconfig['topdir'] = '$(archdir)'
  datarootdir = \
  expanded['datarootdir'] = "#{prefix}/share"
  mkconfig['datarootdir'] = '$(prefix)/share'
  expanded['ridir'] = "#{datarootdir}/ri"
  mkconfig['ridir'] = '$(datarootdir)/ri'

  expanded['CPP'] = "#{cc} -E"
  mkconfig['CPP'] = '$(CC) -E'

  ldshared_flags = Truffle::Platform.darwin? ? '-dynamic -bundle' : '-shared'
  expanded['LDSHARED'] = "#{cc} #{ldshared_flags}"
  mkconfig['LDSHARED'] = "$(CC) #{ldshared_flags}"
  expanded['LDSHAREDXX'] = "#{cxx} #{ldshared_flags}"
  mkconfig['LDSHAREDXX'] = "$(CXX) #{ldshared_flags}"

  cflags = \
  expanded['cflags'] = "#{optflags} #{debugflags} #{warnflags}"
  mkconfig['cflags'] = '$(optflags) $(debugflags) $(warnflags)'
  expanded['CFLAGS'] = cflags
  mkconfig['CFLAGS'] = '$(cflags)'

  cxxflags = \
  expanded['cxxflags'] = "#{optflags} #{debugflags} #{warnflags}"
  mkconfig['cxxflags'] = '$(optflags) $(debugflags) $(warnflags)'
  expanded['CXXFLAGS'] = cxxflags
  mkconfig['CXXFLAGS'] = '$(cxxflags)'
  cppflags_hardcoded = Truffle::Platform.darwin? ? ' -D_DARWIN_C_SOURCE' : ''
  expanded['CPPFLAGS'] = "#{cppflags_hardcoded} #{defs} #{cppflags}"
  mkconfig['CPPFLAGS'] = "#{cppflags_hardcoded} $(DEFS) $(cppflags)"

  launcher = Truffle::Boot.get_option 'launcher'
  launcher = "#{bindir}/#{ruby_install_name}" if launcher.empty?
  RUBY = launcher

  def self.ruby
    RUBY
  end

  def RbConfig.expand(val, config = CONFIG)
    newval = val.gsub(/\$\$|\$\(([^()]+)\)|\$\{([^{}]+)\}/) do
      var = $&
      if !(v = $1 || $2)
        '$'
      elsif key = config[v = v[/\A[^:]+(?=(?::(.*?)=(.*))?\z)/]]
        pat, sub = $1, $2
        config[v] = false
        config[v] = RbConfig.expand(key, config)
        key = key.gsub(/#{Regexp.quote(pat)}(?=\s|\z)/n) { sub } if pat
        key
      else
        var
      end
    end
    val.replace(newval) unless newval == val
    val
  end

end

CROSS_COMPILING = nil
