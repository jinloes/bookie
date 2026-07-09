
# JspPropertyGroupDescriptor


## Properties

Name | Type
------------ | -------------
`buffer` | string
`trimDirectiveWhitespaces` | string
`errorOnUndeclaredNamespace` | string
`deferredSyntaxAllowedAsLiteral` | string
`errorOnELNotFound` | string
`pageEncoding` | string
`scriptingInvalid` | string
`includePreludes` | Array&lt;string&gt;
`includeCodas` | Array&lt;string&gt;
`defaultContentType` | string
`elIgnored` | string
`isXml` | string
`urlPatterns` | Array&lt;string&gt;

## Example

```typescript
import type { JspPropertyGroupDescriptor } from ''

// TODO: Update the object below with actual values
const example = {
  "buffer": null,
  "trimDirectiveWhitespaces": null,
  "errorOnUndeclaredNamespace": null,
  "deferredSyntaxAllowedAsLiteral": null,
  "errorOnELNotFound": null,
  "pageEncoding": null,
  "scriptingInvalid": null,
  "includePreludes": null,
  "includeCodas": null,
  "defaultContentType": null,
  "elIgnored": null,
  "isXml": null,
  "urlPatterns": null,
} satisfies JspPropertyGroupDescriptor

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as JspPropertyGroupDescriptor
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


