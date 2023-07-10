package voicerecipeserver.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import voicerecipeserver.api.RecipeApi;
import voicerecipeserver.model.dto.CommentDto;
import voicerecipeserver.model.dto.IdDto;
import voicerecipeserver.model.dto.MarkDto;
import voicerecipeserver.model.dto.RecipeDto;
import voicerecipeserver.model.exceptions.BadRequestException;
import voicerecipeserver.model.exceptions.NotFoundException;
import voicerecipeserver.services.RecipeService;

import java.util.List;

//TODO категории в рецепты добавить

@CrossOrigin(maxAge = 1440)
@RestController
public class RecipeApiController implements RecipeApi {
    private final RecipeService service;

    @Autowired
    public RecipeApiController(RecipeService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<RecipeDto> recipeIdGet(Long id) throws NotFoundException {
        return service.getRecipeById(id);
    }

    @Override
    public ResponseEntity<IdDto> recipePost(RecipeDto recipeDto) throws NotFoundException, BadRequestException {
        return service.addRecipe(recipeDto);
    }

    @Override
    public ResponseEntity<IdDto> recipeUpdate(RecipeDto recipeDto) throws NotFoundException, BadRequestException {
        return service.updateRecipe(recipeDto);
    }

    public ResponseEntity<List<RecipeDto>> recipeSearchNameGet(String name, Integer limit) throws NotFoundException {
        return service.searchRecipesByName(name, limit);
    }

    @Override
    public ResponseEntity<IdDto> markPost(MarkDto mark) throws BadRequestException, NotFoundException {
        return service.addRecipeMark(mark);
    }

    @Override
    public ResponseEntity<IdDto> markUpdate(MarkDto mark) throws BadRequestException, NotFoundException {
        return service.updateRecipeMark(mark);
    }

    @Override
    public ResponseEntity<Void> markDelete(Long id) {
        return service.deleteRecipeMark(id);
    }

    @Override
    public ResponseEntity<IdDto> commentPost(CommentDto commentDto) throws NotFoundException, BadRequestException {
        return service.postComment(commentDto);
    }

    @Override
    public ResponseEntity<IdDto> commentUpdate(CommentDto commentDto) throws NotFoundException, BadRequestException {
        return service.updateComment(commentDto);
    }

    @Override
    public ResponseEntity<Void> commentDelete(Long id) {
        return service.deleteComment(id);
    }
}
