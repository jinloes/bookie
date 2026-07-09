
# FilterRegistration


## Properties

Name | Type
------------ | -------------
`servletNameMappings` | Array&lt;string&gt;
`urlPatternMappings` | Array&lt;string&gt;
`name` | string
`className` | string
`initParameters` | { [key: string]: string; }

## Example

```typescript
import type { FilterRegistration } from ''

// TODO: Update the object below with actual values
const example = {
  "servletNameMappings": null,
  "urlPatternMappings": null,
  "name": null,
  "className": null,
  "initParameters": null,
} satisfies FilterRegistration

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FilterRegistration
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


