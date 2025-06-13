import http from 'k6/http';
import { sleep } from 'k6';

export default function () {
    http.get('https://petstorepetservice-api.azurewebsites.net/petstorepetservice/v2/pet/1');
    sleep(1); // Adjust the sleep time as needed
}
