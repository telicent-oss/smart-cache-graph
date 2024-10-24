## Yaml Config Parser and RDF Config Generator
A tool for translating Yaml files to standard Fuseki config files. Consists of a Yaml parser and RDF generator.

[Documentation](documentation.md)

### User guide

The `YAMLConfigParser` class contains the `run` method which takes the path to the Yaml file as an argument.
`run` return a `ConfigStruct` - a data structure mimicking the format of the Yaml config file and containing
all of its fields' values.

This `ConfigStruct` can then be passed as an argument to `RDFConfigGenerator`'s `createRDFModel` method which will
return an RDF model of the config file in the [standard Fuseki format](https://jena.apache.org/documentation/fuseki2/fuseki-configuration.html). That model can then be written to a TTL file.