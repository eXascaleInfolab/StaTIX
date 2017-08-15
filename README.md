# StaTIX
Statistical Type Inference (both fully automatic and semi supervised), a Master Project of Soheil Roshankish.

\authors: (c) Soheil Roshankish, Artem Lutov <artem@exascale.info>  
\license:  [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)  
\organization: [eXascale Infolab](http://exascale.info/)  
\date: 2017-08

## Content
- [Overview](#overview)
- [Requirements](#requirements)
- [Usage](#usage)
- [Related Projects](#related-projects)

## Overview

## Requirements
*StaTIX* uses *DAOC* clustering library and *Apache [Commons CLI](https://commons.apache.org/proper/commons-cli/)*. Both libraries are included into the repository and located in the `/lib` dir.

*DAOC* is a C++ clustering library with [SWIG](http://www.swig.org/)-generated Java interface. The provided native library (`libdaoc.so`) is built on *Ubuntu 16.04 x64* (and might also work in the [Ubuntu console of Windows 10 x64](https://www.windowscentral.com/how-install-bash-shell-command-line-windows-10)). *DAOC* should be rebuilt from the sources to run *StaTIX* on other platforms.

## Usage

## Related Projects
- [xmeasures](https://github.com/eXascaleInfolab/xmeasures)  - Extrinsic clustering measures evaluation for the multi-resolution clustering with overlaps (covers): F1_gm for overlapping multi-resolution clusterings with possible unequal node base and standard NMI for non-overlapping clustering on a single resolution.
- [GenConvNMI](https://github.com/eXascaleInfolab/GenConvNMI) - Overlapping NMI evaluation that is (unlike `onmi`) compatible with the original NMI and suitable for both overlapping and multi resolution (hierarchical) clusterings.
