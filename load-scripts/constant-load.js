import http from 'k6/http';
import { sleep } from 'k6';

export default function () {
    http.get('https://petstorepetservice.mangomoss-81f4e12f.eastus.azurecontainerapps.io/petstorepetservice/v2/pet/1');
    sleep(1); // Adjust the sleep time as needed
}
