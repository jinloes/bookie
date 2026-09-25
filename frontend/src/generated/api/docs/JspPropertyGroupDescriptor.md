# JspPropertyGroupDescriptor

## Properties

| Name                             | Type                |
| -------------------------------- | ------------------- |
| `buffer`                         | string              |
| `elIgnored`                      | string              |
| `isXml`                          | string              |
| `urlPatterns`                    | Array&lt;string&gt; |
| `errorOnELNotFound`              | string              |
| `pageEncoding`                   | string              |
| `scriptingInvalid`               | string              |
| `includePreludes`                | Array&lt;string&gt; |
| `includeCodas`                   | Array&lt;string&gt; |
| `defaultContentType`             | string              |
| `deferredSyntaxAllowedAsLiteral` | string              |
| `trimDirectiveWhitespaces`       | string              |
| `errorOnUndeclaredNamespace`     | string              |

## Example

```typescript
import type { JspPropertyGroupDescriptor } from '';

// TODO: Update the object below with actual values
const example = {
  buffer: null,
  elIgnored: null,
  isXml: null,
  urlPatterns: null,
  errorOnELNotFound: null,
  pageEncoding: null,
  scriptingInvalid: null,
  includePreludes: null,
  includeCodas: null,
  defaultContentType: null,
  deferredSyntaxAllowedAsLiteral: null,
  trimDirectiveWhitespaces: null,
  errorOnUndeclaredNamespace: null,
} satisfies JspPropertyGroupDescriptor;

console.log(example);

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example);
console.log(exampleJSON);

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as JspPropertyGroupDescriptor;
console.log(exampleParsed);
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)
