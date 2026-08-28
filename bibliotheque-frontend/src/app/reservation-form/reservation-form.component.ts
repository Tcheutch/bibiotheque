import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Books } from '../_model/books';
import { ReservationRequest } from '../_model/reservation';
import { Users } from '../_model/users';

@Component({
  selector: 'app-reservation-form',
  templateUrl: './reservation-form.component.html',
  styleUrls: ['./reservation-form.component.css']
})
export class ReservationFormComponent {

  @Input() livres: Books[] = [];
  @Input() adherents: Users[] = [];

  @Output() creer = new EventEmitter<ReservationRequest>();

  livreId?: number;
  adherentId?: number;

  onSubmit() {
    if (!this.livreId || !this.adherentId) {
      return;
    }
    this.creer.emit({ livreId: this.livreId, adherentId: this.adherentId });
    this.livreId = undefined;
    this.adherentId = undefined;
  }
}
