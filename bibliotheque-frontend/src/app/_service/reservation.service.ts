import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { Books } from '../_model/books';
import { Reservation, ReservationRequest, ReservationStatus } from '../_model/reservation';
import { Users } from '../_model/users';
import { BooksService } from './books.service';
import { UsersService } from './users.service';

@Injectable({
  providedIn: 'root'
})
export class ReservationService {

  private baseURL = "http://localhost:8080/api/reservations";

  constructor(
    private httpClient: HttpClient,
    private booksService: BooksService,
    private usersService: UsersService
  ) { }

  listerReservations(statut?: ReservationStatus): Observable<Reservation[]> {
    const params: { [param: string]: string } = {};
    if (statut) {
      params['statut'] = statut;
    }
    return this.httpClient.get<Reservation[]>(`${this.baseURL}`, { params });
  }

  creerReservation(reservation: ReservationRequest): Observable<Reservation> {
    return this.httpClient.post<Reservation>(`${this.baseURL}`, reservation);
  }

  annulerReservation(id: number): Observable<Reservation> {
    return this.httpClient.patch<Reservation>(`${this.baseURL}/${id}/annuler`, {});
  }

  listerLivres(): Observable<Books[]> {
    return this.booksService.getBooksList();
  }

  listerAdherents(): Observable<Users[]> {
    return this.usersService.getUsersList();
  }
}
