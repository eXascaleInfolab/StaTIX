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

StaTIX performs *statistical type inference for the RDF datasets* in fully automatic fashion with possibility to use semi supervised mode. In the semi supervised mode, either *a)* the sample of the processing dataset *prelabeled with the type* properties should be provided, or *b)* another dataset should be specified with the present type properties and, desirably, similar structure to the processing dataset. The input RDF dataset(s) should be specified in the [N3 format](https://www.w3.org/TeamSubmission/n3/): `<subject> <property> <object> .`.  
Types that are clusters of the RDF triple subjects are identified in the scope of the whole input dataset with automatic scale identification for each cluster. The scale for all clusters can be manually forced in case specific macro or micro level clustering is required.

The output results are clusters in the [.cnl format](https://github.com/eXascaleInfolab/PyCABeM/blob/master/formats/format.cnl) (space separated list of members). Each cluster correspond to the type and has members represented by the subject ids. Subject ids are generated sequentially starting from `0` for all unique subjects in the input dataset.

## Requirements
*StaTIX* uses *DAOC* clustering library and *Apache [Commons CLI](https://commons.apache.org/proper/commons-cli/)* arguments parser. Both libraries are included into the repository and located in the `/lib` dir.

*DAOC* is a C++ clustering library with [SWIG](http://www.swig.org/)-generated Java interface. The provided native library (`libdaoc.so`) is built on *Ubuntu 16.04 x64* (and might also work in the [Ubuntu console of Windows 10 x64](https://www.windowscentral.com/how-install-bash-shell-command-line-windows-10)). *DAOC* should be rebuilt from the sources to run *StaTIX* on other platforms.

## Usage

```
./run.sh  -h
Usage: ./run.sh [OPTIONS...] <inputfile.rdf>
Statistical type inference in fully automatic and semi supervised modes
Options:[OPTIONS...] <inputfile.rdf>
Statistical type inference in fully automatic and semi supervised modes
Options:
 -a,--all-scales           Fine-grained type inference on all scales
                           besides the macro scale
 -f,--filter               Filter out from the resulting clusters all
                           subjects that do not have #type property in the
                           input dataset, used for the type inference
                           evaluation
 -g,--ground-truth <arg>   The ground-truth sample (subset of the input
                           dataset or another similar dataset with the
                           specified type properties)
 -h,--help                 Show usage
 -o,--output <arg>         Output file, default: <inpfile>.cnl
 -s,--scale <arg>          Scale (gamma parameter of the clustering), -1
                           is automatic scale inference for each cluster,
                           >=0 is the forced static scale (<=1 for the
                           macro clustering); default: -1
```
To infer types without the ground-truth available with the implicit output to the `inpDataset.cnl`: `./run.sh inpDataset.rdf`.  
To infer types with available ground-truth for the sampled reduced dataset or using another typed dataset with similar structure, performing output to the `results.cnl`: `./run.sh -g gtSample.rdf -o results.cnl inpDataset.rdf`.  
To infer types on multiple resolution levels (besides the whole dataset scope): `./run.sh -a inpDataset.rdf`.  

### Compilation

```
./build.sh [-p] [-c] [<outdir>]
  -p,--pack - build the tarball besides the executables
  -c,--classes  - retain classes after the build, useful for the frequent
    modification and recompilation of some files.
    
    Compilation or the single file (.java to .class):
    $ javac -cp lib/\*:src -d classes/ src/info/exascale/SimWeighted/main.java
```
The compilation requires JDK and verified on OpenJDK 8/9 x64.  
The build yields `statix.jar` with all requirements in the output directory (`.` by default) and optionally packs all these files to the tarball `statix.tar.gz`.

### Distribution

Compilation generates `statix.tar.gz` tarball with all requirements ready for the distribution. Also the tarball can be generated from the executables using the `pack.sh` script.

## Related Projects

- [xmeasures](https://github.com/eXascaleInfolab/xmeasures)  - Extrinsic clustering measures evaluation for the multi-resolution clustering with overlaps (covers): F1_gm for overlapping multi-resolution clusterings with possible unequal node base and standard NMI for non-overlapping clustering on a single resolution.
- [GenConvNMI](https://github.com/eXascaleInfolab/GenConvNMI) - Overlapping NMI evaluation that is (unlike `onmi`) compatible with the original NMI and suitable for both overlapping and multi resolution (hierarchical) clusterings.
