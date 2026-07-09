
# UpdateExpenseRequest


## Properties

Name | Type
------------ | -------------
`amount` | number
`description` | string
`date` | Date
`category` | string
`propertyId` | number
`payerId` | number
`receiptOneDriveId` | string
`receiptFileName` | string

## Example

```typescript
import type { UpdateExpenseRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "amount": null,
  "description": null,
  "date": null,
  "category": null,
  "propertyId": null,
  "payerId": null,
  "receiptOneDriveId": null,
  "receiptFileName": null,
} satisfies UpdateExpenseRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as UpdateExpenseRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


