# JspPropertyGroupDescriptor

## Properties

| Name                             | Type                |
| -------------------------------- | ------------------- |
| `buffer`                         | string              |
| `pageEncoding`                   | string              |
| `errorOnELNotFound`              | string              |
| `includePreludes`                | Array&lt;string&gt; |
| `scriptingInvalid`               | string              |
| `includeCodas`                   | Array&lt;string&gt; |
| `defaultContentType`             | string              |
| `elIgnored`                      | string              |
| `isXml`                          | string              |
| `urlPatterns`                    | Array&lt;string&gt; |
| `trimDirectiveWhitespaces`       | string              |
| `errorOnUndeclaredNamespace`     | string              |
| `deferredSyntaxAllowedAsLiteral` | string              |

## Example

```typescript
import type { JspPropertyGroupDescriptor } from '';

// TODO: Update the object below with actual values
const example = {
  buffer: null,
  pageEncoding: null,
  errorOnELNotFound: null,
  includePreludes: null,
  scriptingInvalid: null,
  includeCodas: null,
  defaultContentType: null,
  elIgnored: null,
  isXml: null,
  urlPatterns: null,
  trimDirectiveWhitespaces: null,
  errorOnUndeclaredNamespace: null,
  deferredSyntaxAllowedAsLiteral: null,
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
