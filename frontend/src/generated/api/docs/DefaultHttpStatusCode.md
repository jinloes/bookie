
# DefaultHttpStatusCode


## Properties

Name | Type
------------ | -------------
`error` | boolean
`is1xxInformational` | boolean
`is2xxSuccessful` | boolean
`is3xxRedirection` | boolean
`is4xxClientError` | boolean
`is5xxServerError` | boolean

## Example

```typescript
import type { DefaultHttpStatusCode } from ''

// TODO: Update the object below with actual values
const example = {
  "error": null,
  "is1xxInformational": null,
  "is2xxSuccessful": null,
  "is3xxRedirection": null,
  "is4xxClientError": null,
  "is5xxServerError": null,
} satisfies DefaultHttpStatusCode

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as DefaultHttpStatusCode
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


