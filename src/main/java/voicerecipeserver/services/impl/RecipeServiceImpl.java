package voicerecipeserver.services.impl;

import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import voicerecipeserver.model.dto.IdDto;
import voicerecipeserver.model.dto.RecipeDto;
import voicerecipeserver.model.entities.*;
import voicerecipeserver.model.exceptions.BadRequestException;
import voicerecipeserver.model.exceptions.NotFoundException;
import voicerecipeserver.respository.*;
import voicerecipeserver.services.RecipeService;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
public class RecipeServiceImpl implements RecipeService {

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final MeasureUnitRepository measureUnitRepository;
    private final IngredientsDistributionRepository ingredientsDistributionRepository;
    private final StepRepository stepRepository;
    private UserRepository userRepository;

    private final ModelMapper mapper;

    // TODO не ну это колбасу точно убрать надо
    @Autowired
    public RecipeServiceImpl(StepRepository stepRepository, RecipeRepository recipeRepository,
                             IngredientRepository ingredientRepository, MeasureUnitRepository measureUnitRepository,
                             IngredientsDistributionRepository ingredientsDistributionRepository, ModelMapper mapper) {
        this.stepRepository = stepRepository;
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.measureUnitRepository = measureUnitRepository;
        this.ingredientsDistributionRepository = ingredientsDistributionRepository;
        this.mapper = mapper;
    }

    @Autowired
    private void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public ResponseEntity<RecipeDto> getRecipeById(Long id) throws NotFoundException {
        Optional<Recipe> recipeOptional = recipeRepository.findById(id);

        if (recipeOptional.isEmpty()) {
            throw new NotFoundException("Не удалось найти рецепт с id: " + id);
        }

        Recipe recipe = recipeOptional.get();
        RecipeDto recipeDto = mapper.map(recipe, RecipeDto.class);
        return new ResponseEntity<>(recipeDto, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<IdDto> addRecipe(RecipeDto recipeDto) throws NotFoundException, BadRequestException {
        Recipe recipe = mapper.map(recipeDto, Recipe.class);

        Optional<User> author = userRepository.findByUid(recipe.getAuthor().getUid());

        if (author.isEmpty()) {
            throw new NotFoundException("Не удалось найти автора с uid: " + recipe.getAuthor().getUid());
        } else {
            recipe.setAuthor(author.get());
        }

        recipe.setId(null);

        // через маппер можно сделать путем добавления конвертера. Только вот код
        // там будет хуже, его будет сильно больше, а производительность вряд ли вырастет
        for (Step step : recipe.getSteps()) {
            step.setRecipe(recipe);
        }

        HashSet<String> ingredientsInRecipe = new HashSet<>();
        for (IngredientsDistribution ingredientsDistribution : recipe.getIngredientsDistributions()) {
            ingredientsDistribution.setRecipe(recipe);

            Ingredient receivedIngredient = ingredientsDistribution.getIngredient();
            String ingredientName = receivedIngredient.getName();

            if (ingredientsInRecipe.contains(ingredientName)) {
                throw new BadRequestException("Ингредиент встречается дважды: " + ingredientName);
            } else {
                ingredientsInRecipe.add(receivedIngredient.getName());
            }

            Optional<Ingredient> ingredientFromRepo = ingredientRepository.findByName(ingredientName);
            if (ingredientFromRepo.isEmpty()) {
                receivedIngredient.setId(null);
            } else {
                ingredientsDistribution.setIngredient(ingredientFromRepo.get());
            }
            //TODO в идеале бы настроить для recipe save так, чтобы он сохранял measureUnit, ingredient, если их нет
            // в БД. Хочется убрать лишний find.

            Optional<MeasureUnit> measureUnitOptional = measureUnitRepository.findByName(
                    ingredientsDistribution.getUnit().getName());
            if (measureUnitOptional.isEmpty()) {
                ingredientsDistribution.setUnit(measureUnitRepository.save(ingredientsDistribution.getUnit()));
            } else {
                ingredientsDistribution.setUnit(measureUnitOptional.get());
            }
        }
        recipeRepository.save(recipe);
        return new ResponseEntity<>(new IdDto().id(recipe.getId()), HttpStatus.OK);
    }

    @Override
    public ResponseEntity<List<RecipeDto>> searchRecipesByName(String name, Integer limit) throws NotFoundException {
        if (limit == null) {
            limit = 0;
        }
        List<Recipe> recipes = recipeRepository.findByNameContaining(name, limit);

        if (recipes.isEmpty()) {
            throw new NotFoundException("Не удалось найти рецепты с подстрокой: " + name);
        }
        List<RecipeDto> recipeDtos = mapper.map(recipes, new TypeToken<List<RecipeDto>>() {}.getType());

        return new ResponseEntity<>(recipeDtos, HttpStatus.OK);
    }
}
