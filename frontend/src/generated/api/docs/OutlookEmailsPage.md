
# OutlookEmailsPage


## Properties

Name | Type
------------ | -------------
`emails` | [Array&lt;OutlookEmail&gt;](OutlookEmail.md)
`page` | number
`hasMore` | boolean

## Example

```typescript
import type { OutlookEmailsPage } from ''

// TODO: Update the object below with actual values
const example = {
  "emails": null,
  "page": null,
  "hasMore": null,
} satisfies OutlookEmailsPage

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as OutlookEmailsPage
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


