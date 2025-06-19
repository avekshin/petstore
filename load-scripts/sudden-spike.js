import http from 'k6/http';
import { sleep } from 'k6';

export let options = {
    stages: [
        { duration: '1m', target: 10 },    // Ramp-up to 10 users over 1 minute
        { duration: '1m', target: 70 },    // Spike to 70 users in 1 minute
        { duration: '2m', target: 70 },    // Stay at 70 users for 5 minutes
    ],
};

export default function () {
    http.get('https://petstorepetservice.mangomoss-81f4e12f.eastus.azurecontainerapps.io/petstorepetservice/v2/pet/1');
    sleep(1);
}
